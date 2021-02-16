(ns cmgr.core
  (:require [cmgr.state]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [ring.adapter.jetty :as ringa]
            [ring.util.response :as ringu]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]])
  (:gen-class))

(defn read-config
  "Read .cmgr in the user's home dir. Strip comments and blank lines."
  []
  (into {} conj
        (map (fn [xx] (let [[kstr vstr] (str/split xx #"\s+")] {(keyword kstr) vstr}))
             (remove #(re-matches #"^\s*;;.*|^\s*$" %)
                     (re-seq #"(?m)^.*$"
                             (slurp (str(System/getenv "HOME") "/.cmgr")))))))


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

;; Functions in clojure have two string-ish name formats:
;; #function[machine.core/if-logged-in]
;; "machine.core$if_logged_in@5df2f577"
;; The regex to identify an "if-" function varies depending on type of fn-name.
;; (re-find #"\$if_" (str fn-name))
;; (re-find #"/if-" (str fn-name))

(defn user-input
  "Utility function for mocking up state machine execution with user input for the if- state tests."
  [fn-name]
  (printf "user-input fn-name: %s\n" fn-name)
  (cond (= fn-name (resolve 'fntrue)) (do (printf "Have fntrue, returning true.\n") (cmgr.state/fntrue))
        (= fn-name (resolve 'fnfalse)) (do (printf "Have fnfalse, returning false.\n" (cmgr.state/fnfalse)))
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
  "Use this version of traverse with user-input above for testing."
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
  (if (nil? state)
    nil
    (loop [tt (state cmgr.state/table)]
      (let [curr (first tt)
            test-result ((nth curr 0))]
        (cond (and test-result (some? (nth curr 1))) (traverse (nth curr 1))
              (seq (rest tt)) (recur (rest tt))
              :else nil)))))


;; Be specific that we only do dynamic requests to the /cmgr endpoint.
;; Anything else is a 404 here, and wrap-file will try to load static content aka a file.
(defn handler
  [request]
  (if (not= "/cmgr" (:uri request))
    ;; calling code in ring.middleware.file expects a status 404 when the handler doesn't have an answer.
    {:status 404 :body (format "Unknown request %.40s ..." (:uri request))}
    (let [temp-params (as-> request yy
                        (:form-params yy) ;; We only support POST requests now.
                        (reduce-kv #(assoc %1 (keyword %2) (clojure.string/trim %3))  {} yy)
                        (assoc yy
                               :d_state (keyword (:d_state yy))))]
      (cmgr.state/set-params temp-params)
      (traverse (or (:d_state temp-params) :page_search))
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body @cmgr.state/html-out})))


;; Deep inside ring.middleware.file, if file-request can't find a file, it returns a nil response map which results in a 500 error.
;; I really think it should return a 404, since a missing file isn't generally considered a hard fail.

;; The handler is basically a callback that the wrappers may choose to run. Assuming the wrappers call the handler, the wrappers
;; can modify the request before passing it to the handler, and/or modify the response from the handler.
(def app
  (-> handler
      (wrap-file export-path {:allow-symlinks? true
                              :prefer-handler? true})
      (wrap-multipart-params)
      (wrap-params)))


;; Unclear how defonce and lein ring server headless will play together.
(defn ds []
  (defonce server (ringa/run-jetty app {:port 8080 :join? false})))


(defn -main
  "Parse the states.dat file."
  [& args]
  (printf "args: %s\n" args)
  ;; Workaround for the namespace changing to "user" after compile and before -main is invoked
  (in-ns true-ns)
  (cmgr.state/set-config (read-config))
  (ds)
  (prn "server: " server)
  (.start server))
