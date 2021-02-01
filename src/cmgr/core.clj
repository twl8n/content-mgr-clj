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
    (loop [tt (state @cmgr.state/table)
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
    (loop [tt (state @cmgr.state/table)]
      (let [curr (first tt)
            test-result ((nth curr 0))]
        (printf "curr=%s\n" curr)
        (cond test-result (if (some? (nth curr 1))
                              (traverse (nth curr 1))
                              nil)
              (seq (rest tt)) (recur (rest tt))
              :else nil)))))


;; (defn expense-mgr-handler 
;;   "Expense link manager."
;;   [request]
;;   (if (empty? (:params request))
;;     nil
;;     (let [temp-params (reduce-kv #(assoc %1 %2 (clojure.string/trim %3))  {} (:params request))
;;           _ (prn "tp: " temp-params)
;;           action (get temp-params "action")
;;           ras  request
;;           nice-date (check-limit-date temp-params)
;;           [using-year using-month] (check-uy {:date nice-date
;;                                               :using-year (or (get temp-params "using_year") "")
;;                                               :using-month (or (get temp-params "using_month") "")})
;;           _ (prn "nice-date: " nice-date " limit-date: " (get temp-params "limit_date") " date: " (get temp-params "date") " uy: " using-year " um: " using-month)
;;           ;; Add :using-year, replace "date" value with a better date value
;;           working-params (merge temp-params
;;                                 {:using-year using-year
;;                                  :using-month using-month
;;                                  :limit-date nice-date
;;                                  "date" nice-date})
;;           ;; rmap is a list of records from the db, will full category data
;;           rmap (request-action working-params action)]
;;       (prn "wp: " working-params)
;;       (reply-action rmap action working-params))))

(defn handler
  [request]
  (let [temp-params (reduce-kv #(assoc %1 %2 (clojure.string/trim %3))  {} (:params request))
          d_state (or (get temp-params "d_state" :page_search))]
      (traverse :page_search)
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body @cmgr.state/html-out}
      ))


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
