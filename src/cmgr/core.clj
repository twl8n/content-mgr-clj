(ns cmgr.core
  (:require [cmgr.state :refer :all]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [ring.adapter.jetty :as ringa]
            [ring.util.response :as ringu]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]])
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
    (loop [tt (state cmgr.state/table)
           xx 1]
      (let [curr (first tt)
            test-result (user-input (nth curr 0))]
        ;; (printf "curr=%s\n" curr)
        (cond (and test-result (some? (nth curr 1))) (traverse-debug (nth curr 1))
              (seq (rest tt)) (recur (rest tt) (inc xx))
              :else nil)))))


(defn traverse
  [state]
  ;; (printf "state=%s\n" state)
  (if (nil? state)
    nil
    (loop [tt (state cmgr.state/table)]
      (let [curr (first tt)
            _ (prn (nth curr 0))
            test-result ((nth curr 0))]
        ;; (printf "curr=%s test-result: %s\n" curr test-result)
        (cond (and test-result (some? (nth curr 1))) (traverse (nth curr 1))
              (seq (rest tt)) (recur (rest tt))
              :else nil)))))

(defn handler
  [request]
  (let [temp-params (as-> request yy
                      (:params yy)
                      (reduce-kv #(assoc %1 (keyword %2) (clojure.string/trim %3))  {} yy)
                      (assoc yy
                             :d_state (keyword (:d_state yy)))
                      )]
    ;; An unfinished thought.
    ;; (map (fn [xx] (cmgr.state/add-state xx)))
    (cmgr.state/set-params temp-params)
    ;; (pp/pprint request)
    (pp/pprint (str "@params: " @cmgr.state/params))
    (traverse (or (:d_state temp-params) :page_search))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body @cmgr.state/html-out}
    ))

(comment
  (let [request {:params {"d_state" "page_search"
                          "findme" "foo"
                          "ginfo" "content_manager,twl"
                          "page_pk" "2030"
                          "item" "Edit content"}}
        tp (as-> request yy
             (:params yy)
             (reduce-kv #(assoc %1 (keyword %2) (clojure.string/trim %3))  {} yy)
            (assoc yy :d_state (keyword (:d_state yy)))
            )]
  tp)
  )


(def app
  (wrap-multipart-params (wrap-params handler)))

;; Unclear how defonce and lein ring server headless will play together.
(defn ds []
  (defonce server (ringa/run-jetty app {:port 8080 :join? false})))


(defn -main
  "Parse the states.dat file."
  [& args]
  (printf "args: %s\n" args)
  ;; Workaround for the namespace changing to "user" after compile and before -main is invoked
  (in-ns true-ns)

  (ds)
  (prn "server: " server)
  (.start server)
  
  )
