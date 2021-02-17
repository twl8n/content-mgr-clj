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

(defn go-again []
  (print "Go again?")
  (flush)
  (let [user-answer (if (= (read-line) "y")
                      true
                      false)]
    (printf "%s\n" user-answer)
    user-answer))

(defn user-input [fn-name]
  (let [fn-result (fn-name)]
    (printf "user-input fn-name: %s returns: %s\n" fn-name fn-result)
    (if (boolean? fn-result)
      fn-result
        (do
          (print "Function" (str fn-result) ": ")
          (flush)
          (let [user-answer (if (= (read-line) "y")
                              true
                              false)]
            (printf "%s\n" user-answer)
            user-answer)))))


;; Loop through tests (nth curr 0) while tests are false, until hitting wait.
;; Stop looping  if test is true, and change to the next-state-edge (nth curr 2).
(defn traverse-debug
  [state]
  (cmgr.state/add-state :test-mode)
  ;; (printf "state=%s\n" state)
  (if (nil? state)
    nil
    (loop [tt (state cmgr.state/table)
           xx 1]
      (let [curr (first tt)
            test-result (user-input (nth curr 0))]
        ;; (printf "curr=%s\n" curr)
        (if (and test-result (some? (nth curr 1)))
          (traverse-debug (nth curr 1))
          (if (seq (rest tt))
            (recur (rest tt) (inc xx))
            nil))))))

;; 2021-02-16
;; If test-result and there is a new state, go to that state. When we return, we're done.
;; Otherwise go to the next function of this state (regardless of the truthy-ness of a test or function return).
;; Always stop when we run out of functions.
;; todo? Maybe stop when the wait function runs. Right now, wait is a no-op.

(defn traverse
  [state]
  (if (nil? state)
    nil
    (loop [tt (state cmgr.state/table)]
      (let [curr (first tt)
            test-result ((nth curr 0))]
        (if (and test-result (some? (nth curr 1)))
          (traverse (nth curr 1))
          (if (seq (rest tt))
            (recur (rest tt))
            nil))))))


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

(defn get-conf [ckey]
  (ckey @cmgr.state/config))

;; Deep inside ring.middleware.file, if file-request can't find a file, it returns a nil response map which results in a 500 error.
;; I really think it should return a 404, since a missing file isn't generally considered a hard fail.

;; The handler is basically a callback that the wrappers may choose to run. Assuming the wrappers call the handler, the wrappers
;; can modify the request before passing it to the handler, and/or modify the response from the handler.

;; We need to dynamically discover the export path at run time, NOT compile time, therefor we must
;; use defn and not def (as you will see in every other ring server example). 
(defn make-app [& args]
  (-> handler
      (wrap-file (get-conf :export-path) {:allow-symlinks? true
                                         :prefer-handler? true})
                 (wrap-multipart-params)
                 (wrap-params)))


;; Unclear how defonce and lein ring server headless will play together.
(defn ds []
  (defonce server (ringa/run-jetty (make-app) {:port 8080 :join? false})))


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
