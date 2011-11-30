(ns i-know-kung-fu.core
  (:use [clojure.java.io])
  (:require [clojure.pprint :as p])
  (:require [clojure.string :as str])
  (:gen-class))

;; constants

(def considered-known-at-num-correct 3)
(def new-ones-batch-size 10)

;; loading and saving

(defn load-stacks [save-file] (load-string (slurp save-file)))
(defn save-stacks [save-file stacks]
  (spit save-file (-> stacks
                    (update-in [:to-ask] vec)
                    (update-in [:not-seen] vec)
                    (update-in [:not-due] vec))))

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

;; introduce new cards :not-seen -> :to-ask

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

;;; IO with user

(defn show-quit [] (println "Have a nice day!"))

(defn show-help [] (println "
Learn kung-fu with flashcards!

Help:
  :q quit
  :h this help message
  :s detailed statistics
"))

(defn show-detailed-status [stacks]
  (println "------------------------------------------------------------")
  (println)
  (println "Active cards and their scores:")
  (println)
  (doseq [[score question] (sort (map (juxt :consecutive-correct :question)
                                      (:to-ask stacks)))]
    (println " " score question))
  (println))

(defn elapsed-time [starting-time]
  (let [total-seconds (int (/ (- (now) starting-time) 1000.0))
        minutes (int (/ total-seconds 60.0))
        seconds (int (- total-seconds (* 60.0 minutes)))]
    (str minutes "m " seconds "s")))

(defn show-basic-stats [stacks starting-time]
  (println "------------------------------------------------------------")
  (println)
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
  (println (:question card))
  (println)
  (let [answer-a (str/lower-case (read-line))
        answer-b (str/lower-case (str (:answer card)))]
    (condp = answer-a
      ":q" :quit
      ":h" :help
      ":s" :stats
      answer-b :correct

      :wrong)))

(defn show-all-done []
  (println "All questioned answered for today.  Take a break."))

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
              :stats (do
                       (show-detailed-status stacks)
                       (recur stacks))
              :wrong (do
                       (show-wrong card)
                       (recur (wrong stacks card)))
              :correct (do
                         (show-correct)
                         (recur (correct stacks card))))))))))

;; import new files

(defn import-new-cards [save-file import-file]
  (let [stack (load-stacks save-file)
        category (do
                   (println "Enter category of new cards: ")
                   (read-line))
        new-cards (for [[q a] (partition 2 (str/split (slurp import-file) #"\n"))]
                    {:category category
                     :question q
                     :answer a})
        updated-stacks (update-in stack [:not-seen] concat new-cards)]
    (do
      (save-stacks save-file updated-stacks)
      (println "File" save-file "loaded as category" category))))

;; main entry point

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

(defn -main
  ([] (show-args-help))
  ([save-file] (ask-loop save-file))
  ([save-file import-file] (import-new-cards save-file import-file)))
