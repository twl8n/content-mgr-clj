(ns machine.core
  (:require [machine.state :refer :all]
            [clojure.string :as str]
            [clojure.pprint :as pp])
  (:gen-class))

;; Workaround for the namespace changing to "user" after compile and before -main is invoked
(def true-ns (ns-name *ns*))

;; http://stackoverflow.com/questions/6135764/when-to-use-zipmap-and-when-map-vector
;; Use (zipmap ...) when you want to directly construct a hashmap from separate sequences of keys and values. The output is a hashmap:

(defn sub-table [edge table]
  (edge table))

(defn go-again []
  (print "Go again?")
  (flush)
  (let [user-answer (if (= (read-line) "y")
                      true
                      false)]
    (printf "%s\n" user-answer)
    user-answer))

;; 2021-01-26 Prompt user for any if- functions, but simply run the other functions which should all return false.
;; If they return false, why do we run them??

;; Functions have two string-ish name formats:
;; #function[machine.core/if-logged-in]
;; "machine.core$if_logged_in@5df2f577"
;; The regex to identify an "if-" function varies depending on type of fn-name.
;; (re-find #"\$if_" (str fn-name))
;; (re-find #"/if-" (str fn-name))

(defn user-input [fn-name]
  (printf "user-input fn-name: %s\n" fn-name)
  (cond (= fn-name (resolve 'fntrue)) (do (printf "Have fntrue, returning true.\n") (fntrue))
        (= fn-name (resolve 'fnfalse)) (do (printf "Have fnfalse, returning false.\n" (fnfalse)))
        (nil? (re-find #"\$if_" (str fn-name))) (fn-name)
        :else
        (do
          (print "Function" (str fn-name) ": ")
          (flush)
          (let [user-answer (if (= (read-line) "y")
                              true
                              false)]
            (printf "%s\n" user-answer)
            user-answer))))


;; Loop through tests (nth curr 0) while tests are false, until hitting wait.
;; Stop looping  if test is true, and change to the next-state-edge (nth curr 2).
(defn traverse-debug
  [state]
  ;; (printf "state=%s\n" state)
  (if (nil? state)
    nil
    (loop [tt (state @machine.state/table)
           xx 1]
      (let [curr (first tt)
            test-result (user-input (nth curr 0))]
        ;; (printf "curr=%s\n" curr)
        (cond test-result (if (some? (nth curr 1))
                              (traverse-debug (nth curr 1))
                              nil)
              (seq (rest tt)) (recur (rest tt) (inc xx))
              :else nil)))))


(defn traverse
  [state]
  (printf "state=%s\n" state)
  (if (nil? state)
    nil
    (loop [tt (state @machine.state/table)]
      (let [curr (first tt)
            test-result ((nth curr 0))]
        (printf "curr=%s\n" curr)
        (cond test-result (if (some? (nth curr 1))
                              (traverse (nth curr 1))
                              nil)
              (seq (rest tt)) (recur (rest tt))
              :else nil)))))


(defn demo []
  (add-state :if-logged-in)
  (add-state :if-moderator)
  (add-state :if-want-dashboard)
  ;; (reset-state)
  ;; (pp/pprint @machine.state/table)
  (traverse :login)
  )

(defn demo2 []
  (reset-state)
  (swap! app-state #(merge % {:if-logged-in true}))
  (println "initial state:" @app-state)
  (traverse :login)
  )

(defn demo3 []
  (reset-state)
  (swap! app-state #(merge % {:if-logged-in true :if-on-dashboard true}))
  (println "initial state:" @app-state)
  (traverse :login))

(defn demo4 []
  (reset-state)
  (swap! app-state #(merge % {:if-logged-in true :if-on-dashboard false :if-want-dashboard true :if-moderator true}))
  (println "initial state:" @app-state)
  (traverse :login))

(defn demo4-debug []
  (reset-state)
  ;; Setting app-state makes no sense in a debug setting. The user will answer all the if- state tests.
  (loop []
    (traverse-debug :login)
    (if (go-again) (recur)
        nil)))


(defn -main
  "Parse the states.dat file."
  [& args]
  (printf "args: %s\n" args)
  ;; Workaround for the namespace changing to "user" after compile and before -main is invoked
  (in-ns true-ns)
  
  (let [first-arg (nth args 0)]
    (if (= first-arg "debug") (traverse-debug :page_search)
        (traverse :page_search))))
