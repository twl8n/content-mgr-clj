(ns cmgr.state
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clostache.parser :as clostache] ;; [clostache.parser :refer [render]]
            [clojure.pprint :as pp]))

(def html-out (atom""))

;; I think db is a "connection"
(def db {:dbtype "sqlite" :dbname "cmgr.db"})

;; 2021-01-31 We could use rs/as-unqualified-lower-maps, but since we're using lowercase in our schema,
;; and since we're used to sql drivers returning the case shown in the schema (or uppercase) we don't have to
;; force everything to lower at this time.

;; with-options works on db or ds
;; I guess ds-opts is a "datasource with options" in next.jdbc parlance.
(def ds-opts (jdbc/with-options db {:return-keys true :builder-fn rs/as-unqualified-maps}))


(defn msg [arg] (printf "%s\n" arg))

(def params (atom {}))

(defn set-params [xx]
  (reset! params xx))
  
(def app-state (atom {}))

(defn reset-state [] 
  (swap! app-state (fn [foo] {})))

(defn add-state [new-kw]
  (swap! app-state #(apply assoc % [new-kw true])))

(defn is-jump? [arg] false)

(defn is-wait?
  "(str (type arg)) is something like class machine.core$wait"
  [arg] (= "$wait" (re-find #"\$wait" (str (type arg)))))

(defn is-return? [arg] false)
(defn jump-to [arg jstack] [arg (cons arg jstack)])

(defn if-logged-in [] (let [rval (@app-state :if-logged-in)] (msg (str "running if-logged-in: " rval)) rval))
(defn if-moderator [] (let [rval (@app-state :if-moderator)] (msg (str "running if-moderator: " rval)) rval))
(defn if-on-dashboard [] (let [rval (@app-state :if-on-dashboard)] (msg (str "if-on-dashboard: " rval)) rval))
(defn if-want-dashboard [] (let [rval (@app-state :if-want-dashboard)] (msg (str "if-want-dashboard: " rval)) rval))

(defn draw-login [] (msg "running draw-login") false)
(defn force-logout [] (msg "forcing logout") (swap! app-state #(apply dissoc %  [:if-logged-in])) false)
(defn draw-dashboard-moderator [] (add-state :if-on-dashboard)  (msg "running draw-dashboard-moderator") false)

(defn draw-dashboard [] (msg "running draw-dashboard") false)

(defn logout [] (msg "running logout") false)
(defn login [] (msg "running login") false)
(defn fntrue [] (msg "running fntrue") true)
(defn fnfalse [] (msg "running fnfalse") false)
(defn wait [] (msg "running wait, returning false") true) ;; return true because wait ends looping over tests
(defn noop [] (printf "running noop\n"))

(defn if-edit []
  (let [ret (= "Edit page" (:edit @params))]
    (swap! params #(dissoc % :edit))
    ret))

(defn if-delete [] )
(defn if-insert [] )

(defn if-item []
  (let [ret (= "Edit content" (:item @params))]
    (swap! params #(dissoc % :item))
  ret))

(defn if-site_gen [] )

(defn if-save []
  (let [ret (= "Save" (:save @params))]
    (swap! params #(dissoc % :save))
    ret))

(defn if-continue []
  (let [ret (= "Save & Continue" (:continue @params))]
    (swap! params #(dissoc % :continue))
    ret))
        
(defn if-next [] )

(defn page_search []
  (let [result-set (jdbc/execute! ds-opts ["select * from page where valid_page=1 order by site_name,page_order"])
        db-data {:dcc_site (mapv (fn [xx] {:site_name (key xx)
                                           :dcc_page (val xx)}) (group-by :site_name result-set))}
        recf (count (:dcc_site db-data))
        ready-data (merge @params
                          {:recordsfound recf
                           :d_state "page_search"
                           :s (if (> recf 1) "s" "")}
                          db-data
                          )
        html-result (clostache/render (slurp "html/page_search.html") ready-data)]
    (reset! html-out html-result)
    )
  )

(reset! params {:page_pk 2030})
(:page_pk @params)

(defn edit_page []
  (let [result-set (jdbc/execute-one! ds-opts ["select * from page where page_pk=?" (:page_pk @params)])
        [ext_one ext_zero] (if (= 1 (:external_url @params)) ["selected" ""] ["" "selected"])
        ready-data (merge result-set
                          {:template "edit_page.html"
                           :d_state "edit_page"
                           :ext_one ext_one
                           :ext_zero ext_zero}
                          )
        html-result (clostache/render (slurp "html/edit_page.html") ready-data)]
    (reset! html-out html-result)))

    ;; do_sql_simple("hondavfr", "", "update page set template='\$template', menu='\$menu', page_title='\$page_title', body_title='\$body_title', page_name='\$page_name', search_string='\$search_string', image_dir='\$image_dir', site_name='\$site_name', site_path='\$site_path', owner='\$owner', page_order='\$page_order', valid_page='\$valid_page', external_url='\$external_url' where page_pk=\$page_pk"); 

(defn save_page []
  (let [result-set (sql/update! ds-opts
                                :page
                                (select-keys @params
                                             [:template :menu :page_title :body_title
                                              :page_name :search_string :image_dir
                                              :site_name :site_path :page_order
                                              :valid_page :external_url :page_pk])
                                {:page_pk (:page_pk @params)})]
    true))

;; [ext_one ext_zero] (if (= 1 (:external_url @params)) ["selected" ""] ["" "selected"])
;;         ready-data (merge @params
;;                           {:template "edit_page.html"
;;                            :d_state "edit_page"
;;                            :ext_one ext_one
;;                            :ext_zero ext_zero}
;;                           )
;;         html-result (clostache/render (slurp "html/edit_page.html") ready-data)]
;;     (reset! html-out html-result)))

(defn next_page [] )

;; default is page_search

(def table
  (atom
   {:page_search
    [[if-edit :edit_page]
     [if-delete :ask_delete_page]
     [if-insert :edit_new_page]
     [if-item :item_search]
     [if-site_gen :site_gen]
     [page_search nil]]

    :edit_page
    [[if-save :save_page]
     [if-continue :save_page_continue]
     [if-next :save_next]
     [edit_page nil]]
    ;; 0	edit_page	$save	  save_page()	  page_search
    ;; 1	edit_page	$continue save_page()	  next
    ;; 2	edit_page	$next	  save_page()	  next
    ;; 3	edit_page	$next	  next_page()	  next
    ;; 4	edit_page	$true	  edit_page()	  wait

    :save_page
    [[save_page nil]
     [page_search nil]]

    :save_page_continue
    [[save_page nil]
     [edit_page nil]]

    :save_next
    [[save_page nil]
     [next_page nil]
     [edit_page nil]]

    }))
    ;; 0	page_search	$edit	  null()	  edit_page
    ;; 1	page_search	$delete	  null()	  ask_delete_page
    ;; 2	page_search	$insert	  null()          edit_new_page
    ;; 3	page_search	$item	  null()	  item_search
    ;; 4	page_search	$site_gen site_gen()	  next
    ;; 5	page_search	$true	  page_search()	  wait

(comment table-part-two
  (atom 
   {
    :site_gen
    [[site_gen nil]
     [fntrue :page_search]]

    :ask_del_page
    [[if-confirm :delete_page]
     [fn-true :page_search]]
    ;; 0	ask_del_page	$confirm  delete_page()	  page_search
    ;; 1	ask_del_page	$true	  ask_del_page()  wait

    :delete_page
    [[delete_page :page_search]]


    :edit_new_page
    [[if-save :insert_page]
     [if-continue :insert_continue]
     [edit_new_page nil]]
    ;; 0	edit_new_page	$save	  insert_page()	  page_search
    ;; 1	edit_new_page	$continue insert_page()	  next
    ;; 2	edit_new_page	$continue clear_cont()	  edit_page
    ;; 3	edit_new_page	$true	  edit_new_page() wait
    
    :insert_page
    [[insert_page :page_search]]
    :insert_continue
    [[insert_page nil]
     [clear_cont :edit_page]]

    :item_search
    [[if-edit :edit_item]
     [if-page-gen :page_gen]
     [if-auto-gen :auto_gen]]
    ;; 0	item_search	$edit	  null()	  edit_item
    ;; 1	item_search	$page_gen page_gen()	  next
    ;; 2	item_search	$auto_gen auto_gen()	  next
    ;; 3	item_search	$true	  item_search()	  wait

    :page_gen
    [[page_gen :item_search]]
    :auto_gen
    [[auto_gen :item_search]]

    :edit_item
    [[if-save :save_item]
     [if-continue :save_item_continue]
     [save_item nil]
     [next_item nil]
     [if-con-pk edit_con_pk]
     [fn-true :item_searchs]]
    ;; 0	edit_item	$save	  save_item()	  item_search
    ;; 1	edit_item	$continue save_item()	  next
    ;; 2	edit_item	$continue edit_item()	  wait
    ;; 3	edit_item	$next	  save_item()	  next
    ;; 4	edit_item	$next	  next_item()	  next
    ;; 5    edit_item       $con_pk	  edit_item()	  wait aka edit_con_pk
    ;; 6 	edit_item	$true 	  null()	  item_search

    :save_item
    [[save_item :item_search]]
    :save_item_continue
    [[save_item nil]
     [edit_item nil]]
    :edit_con_pk
    [[edit_item :item_search]]
    }))


;; {:state-edge [[test-or-func next-state-edge] ...]}
;; (def orig-table
;;   (atom
;;    {:login
;;     [[if-logged-in :pages]
;;      [force-logout nil]
;;      [draw-login nil]
;;      [wait nil]]
    
;;     :login-input
;;     [[if-logged-in :dashboard]
;;      [login :login]]

;;     :pages
;;     [[if-on-dashboard :dashboard-input]
;;      [if-want-dashboard :dashboard]
;;      [wait nil]]

;;     :dashboard
;;     [[if-moderator :dashboard-moderator]
;;      [draw-dashboard]
;;      [wait nil]]

;;     :dashboard-moderator
;;     [[draw-dashboard-moderator :dashboard-input]]

;;     :dashboard-input
;;     [[wait nil]]
;;     }))

