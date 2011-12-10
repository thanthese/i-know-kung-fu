[Learn kung-fu](http://www.youtube.com/watch?v=6vMO3XmNXe4) (or anything else)
with this command-line flashcard program.

## The algorithm

Basically a digital version of the [Leitner
System](http://en.wikipedia.org/wiki/Leitner_system).

There are buckets for cards you view every 0, 1, 2, 4, 8 16, 32, ... days.  A
card is presented when it comes due.  If the user gets it right it goes into
the next bigger bucket, otherwise it goes back to the beginning.  The user has
to get a card right several times in a row before it moves off the starting line.

When the program starts the user is presented with cards that are due today, as
well as a small number of new/forgotten cards.  When the number of new cards
drops below some limit, an addtional small batch of cards from a random
category is injected into the mix.

## To compile

Requires installing [leiningen](https://github.com/technomancy/leiningen).
(Don't worry, it's quick and painless.)  Then,

    $ cd i-know-kung-fu
    $ lein uberjar

## The setup

The program must point to a valid save-file, even on first run.  A minimal
save-file is provided under the `resources` folder.

## To run

    $ java -jar i-know-kung-fu-0.1-standalone.jar save-file

to show the help message, or

    $ java -jar i-know-kung-fu-0.1-standalone.jar save-file

to run in training mode, or

    $ java -jar i-know-kung-fu-0.1-standalone.jar save-file import-file

to perform a batch import.  Sample import files are under the `resources`
folder.
