(ns i-know-kung-fu.core
  (:use [clojure.java.io])
  (:require [clojure.pprint :as p])
  (:require [clojure.string :as str])
  (:gen-class))

;; todo ideas

;; constants

(def considered-known-at-num-correct 3)
(def new-ones-batch-size 10)
(def word-wrap #".{0,65}$|.{0,65} ")

;; DEVELOPMENT shortcuts

;(def stacks (load-stacks "/Users/thanthese/i-know-kung-fu/resources/public/save-files/test.clj"))
;(-main "/Users/thanthese/i-know-kung-fu/resources/public/save-files/test.clj")

;(def stacks (load-stacks "/Users/thanthese/Dropbox/kung-fu.clj"))
;(-main "/Users/thanthese/Dropbox/kung-fu.clj")

;; loading and saving

(defn wrap [delimiter elems]
  (let [closer (condp = delimiter
                 "[" "]"
                 "{" "}")]
    (str delimiter "\n" (apply str (interpose "\n" elems)) closer)))

(def key-order {:category 0
                :question 1
                :answer 2
                :consecutive-correct 3
                :answer-at 4})

(defn print-card [card]
  (str (apply sorted-map-by (fn [a b] (compare (a key-order)
                                               (b key-order)))
              (flatten (vec card)))))

(defn print-cards [cards]
  (wrap "[" (map print-card (sort-by :category cards))))

(defn print-stacks [stacks]
  (wrap "{" (for [pile [:not-seen :to-ask :not-due]]
              (str pile "\n" (print-cards (pile stacks))))))

(defn load-stacks [save-file] (read-string (slurp save-file)))
(defn save-stacks [save-file stacks] (spit save-file (print-stacks stacks)))

;; generic operations on the stacks

(defn remove-card [stacks pile card]
  (update-in stacks [pile] (partial remove (partial = card))))

(defn add-card [stacks pile card]
  (update-in stacks [pile] conj card))

(defn stacks-map
  "Iteratively apply f to stacks for each card.
  f takes the arguments [stacks pile card]."
  [stacks pile f cards]
  (reduce
    (fn [stacks-acc card] (f stacks-acc pile card))
    stacks
    cards))

(defn remove-cards [stacks pile cards] (stacks-map stacks pile remove-card cards))
(defn add-cards    [stacks pile cards] (stacks-map stacks pile add-card    cards))

(defn all-cards [stacks]
  (concat (:to-ask stacks)
          (:not-seen stacks)
          (:not-due stacks)))

;; introduce new cards :not-seen -> :to-ask
(defn show-level-up [cards]
  (let [num (count cards)
        category (:category (first cards))]
    (println)
    (println "  !!!!!!!!!!!!!!!!!!")
    (println "  !!!  Level Up  !!!")
    (println "  !!!!!!!!!!!!!!!!!!")
    (println)
    (println "  Adding" num "cards from:" category)
    (println)
    (println)))

(defn not-learned [stacks]
  (count (filter (fn [card] (< (:consecutive-correct card)
                               considered-known-at-num-correct))
                 (:to-ask stacks))))

(defn initialize-counter-fields [cards]
  (for [card cards]
    (-> card
      (assoc :consecutive-correct 0)
      (assoc :answer-at 0))))

(defn top-cards-from-random-category [stacks]
  (let [random-category (->> (:not-seen stacks)
                          (map :category)
                          distinct shuffle first)]
    (take new-ones-batch-size
          (filter (fn [card] (= (:category card) random-category))
                  (:not-seen stacks)))))

(defn shuffle-in-not-seen-cards [stacks]
  (let [enough-not-learned (>= (not-learned stacks) new-ones-batch-size)]
    (if enough-not-learned
      stacks
      (let [cards (top-cards-from-random-category stacks)]
        (show-level-up cards)
        (-> stacks
          (remove-cards :not-seen cards)
          (add-cards :to-ask (initialize-counter-fields cards))
          (update-in [:to-ask] shuffle))))))

;; re-introduce old cards :not-due -> :to-ask

(defn now [] (.getTime (java.util.Date.)))

(defn due-cards [stacks]
  (filter (fn [card] (<= (:answer-at card) (now)))
          (:not-due stacks)))

(defn shuffle-in-due-cards [stacks]
  (let [cards (due-cards stacks)]
    (-> stacks
      (remove-cards :not-due cards)
      (add-cards :to-ask cards)
      (update-in [:to-ask] shuffle))))

;; react to correct answers

(def delay-unit (* 1000 60 60 20))  ; 20 hours in milliseconds

(def spacing-sequence (concat (repeat considered-known-at-num-correct 0)
                              (iterate (partial * 2) 1)))

(defn wait-time [consecutive-correct]
  (* delay-unit (nth spacing-sequence consecutive-correct)))

(defn non-repeating-shuffle
  "Shuffles :to-ask. Guarantees a new first card."
  [stacks]
  (let [cards (:to-ask stacks)
        a (first cards)
        b (first (rest cards))
        r (rest (rest cards))
        shuffled (cons b (shuffle (cons a r)))]
    (if b
      (assoc stacks :to-ask shuffled)
      stacks)))

(defn correct [stacks card]
  (let [score (inc (:consecutive-correct card))
        marked-right (-> card
                       (assoc :consecutive-correct score)
                       (assoc :answer-at (+ (now)
                                            (wait-time score))))]
    (if (>= score considered-known-at-num-correct)
      (-> stacks  ; learned
        (remove-card :to-ask card)
        (add-card :not-due marked-right)
        (shuffle-in-not-seen-cards))
      (-> stacks  ; not yet learned
        (remove-card :to-ask card)
        (add-card :to-ask marked-right)
        (non-repeating-shuffle)))))

;; react to wrong answers

(defn wrong [stacks card]
  (let [marked-wrong (assoc card :consecutive-correct 0)]
    (-> stacks
      (remove-card :to-ask card)
      (add-card :to-ask marked-wrong))))

;; general category manipulations

(defn delete-category [stacks category]
  (reduce
    (fn [stacks-acc pile]
      (assoc-in stacks-acc [pile]
                (remove (fn [card] (= category (:category card)))
                        (pile stacks))))
    stacks
    (keys stacks)))

(defn all-categories [stacks]
  (->> (all-cards stacks)
    (map :category)
    distinct sort))

(defn count-cards-in-category [stacks category]
  (count (filter (fn [card] (= (:category card) category))
                 (all-cards stacks))))

;;; IO with user

(defn show-args-help []
  (println "
Arguments: save-file [import-file]

- save-file is required and must be fully qualified.
- import-file is optional.  When not specified, program
  runs in quiz mode.  When specified, new cards are
  created from the file.  Import file takes the form:

    Question 1
    Answer 1
    Question 2
    Answer 2
    ...
"))

(defn show-header []
  (println "------------------------------------------------------------")
  (println))

(defn show-quit [] (println "Have a nice day!"))

(defn show-help [] (println "
Learn kung-fu with flashcards!

Help:
  :q quit
  :f score frequencies
  :c show categories and card counts
  :d delete category
  :h this help message
"))

(defn pad-int [num-spaces text]
  (format (str "%" num-spaces "d") text))

(defn show-score-frequencies [stacks]
  (show-header)
  (println "Show how many cards are at each score.")
  (println)
  (println "Score | Count | Interval (days)")
  (doseq [[score cards] (sort (group-by :consecutive-correct
                                        (all-cards stacks)))
          :when (not (nil? score))]
    (println " "
             (pad-int 3 score)
             (pad-int 7 (count cards))
             (pad-int 7 (nth spacing-sequence score))))
  (println))

(defn elapsed-time [starting-time]
  (let [total-seconds (int (/ (- (now) starting-time) 1000.0))
        minutes (int (/ total-seconds 60.0))
        seconds (int (- total-seconds (* 60.0 minutes)))]
    (str minutes "m " seconds "s")))

(defn show-basic-stats [stacks starting-time]
  (show-header)
  (println "  Elapsed time: " (elapsed-time starting-time))
  (println)
  (println " " (count (:not-seen stacks)) "Not seen")
  (println " " (count (:to-ask stacks)) "In play")
  (println " " (count (:not-due stacks)) "Not due"))

(defn show-wrong [card]
  (println)
  (println "XXXXXXXXXXXXX")
  (println "XX  Wrong  XX")
  (println "XXXXXXXXXXXXX")
  (println)
  (println "Correct answer: " (:answer card))
  (println))

(defn show-correct []
  (println)
  (println "  ( Right )")
  (println))

(defn ask [card]
  (println)
  (println "  Categry : " (:category card))
  (println "  Score   : " (:consecutive-correct card))
  (println)
  (doseq [line (re-seq word-wrap (:question card))]
    (println line))
  (println)
  (let [answer-a (str/lower-case (read-line))
        answer-b (str/lower-case (str (:answer card)))]
    (condp = answer-a
      ":q" :quit
      ":h" :help
      ":f" :scores
      ":d" :del-cat
      ":c" :cats
      answer-b :correct
      :wrong)))

(defn show-all-done []
  (println "All questioned answered for today.  Take a break."))

(defn show-all-categories [stacks]
  (show-header)
  (println "All categories:")
  (println)
  (doseq [cat (all-categories stacks)]
    (println " " (count-cards-in-category stacks cat) cat))
  (println)
  (println "  There are" (count (all-cards stacks)) "cards in all.")
  (println))

(defn delete-category-io [stacks]
  (show-all-categories stacks)
  (println)
  (println "Which category would you like to delete?")
  (println)
  (let [cleaner-stacks (delete-category stacks (read-line))]
    (println)
    (println "!!  Deletion complete.  !!")
    (println)
    cleaner-stacks))

;; ask loop

(defn ask-loop [save-file]
  (show-help)
  (let [starting-time (now)]
    (loop [stacks (-> (load-stacks save-file)
                    shuffle-in-due-cards
                    shuffle-in-not-seen-cards)]
      (if (empty? (:to-ask stacks))
        (do
          (save-stacks save-file stacks)
          (show-all-done))
        (do
          (show-basic-stats stacks starting-time)
          (let [card (first (:to-ask stacks))]
            (condp = (ask card)
              :quit (do
                      (save-stacks save-file stacks)
                      (show-quit))
              :help (do
                      (show-help)
                      (recur stacks))
              :scores (do
                       (show-score-frequencies stacks)
                       (recur stacks))
              :del-cat (do
                         (recur (delete-category-io stacks)))
              :cats (do
                      (show-all-categories stacks)
                      (recur stacks))
              :wrong (do
                       (show-wrong card)
                       (recur (wrong stacks card)))
              :correct (do
                         (show-correct)
                         (recur (correct stacks card))))))))))

;; import new files

(defn originals
  "Return a list of only those cards for which
  there is no similar card in stacks."
  [stacks cards]
  (let [all (all-cards stacks)]
    (remove (fn [card-a]
              (some (fn [card-b]
                      (and (= (:question card-a)
                              (:question card-b))
                           (= (:answer card-a)
                              (:answer card-b))))
                    all))
            cards)))

(defn qs [text]
  (str "\"" text "\""))

(defn import-new-cards [save-file import-file]
  (let [stacks (load-stacks save-file)
        QAs (partition 2 (str/split (slurp import-file) #"\n"))
        category (do
                   (show-all-categories stacks)
                   (println "Enter category of new cards: ")
                   (println)
                   (str/trim (read-line)))
        new-cards (for [[q a] QAs]
                    {:category category
                     :question q
                     :answer a})
        valid-cards (originals stacks new-cards)]
    (do
      (save-stacks save-file (update-in stacks [:not-seen] concat valid-cards))
      (println)
      (println (count valid-cards)
               "from file" (qs import-file)
               "loaded into" (qs save-file)
               "as category" (qs category)))))

;; main entry point

(defn -main
  ([] (show-args-help))
  ([save-file] (ask-loop save-file))
  ([save-file import-file] (import-new-cards save-file import-file)))
