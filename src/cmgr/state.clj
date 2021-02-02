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

;; Due to the nature of HTML submit inputs, we have both a key and a value.
;; Really, if the key exists we are in the edit state, so lets just check that we have a key and a non-empty string value.
(defn if-edit []
  (let [tval (:edit @params)
        ret (and (seq tval) tval)]
    (swap! params #(dissoc % :edit))
    (boolean ret)))

(defn if-delete [] )
(defn if-insert [] )

;; (if-arg :item)
(defn if-arg [tkey]
  (let [tval (tkey @params)
        ret (and (seq tval) tval)]
    (swap! params #(dissoc % tkey))
    (boolean ret)))

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


;; do_sql_simple("hondavfr", "", "select con_pk from content where page_fk=\$page_pk and valid_content<>0 and item_order>\$item_order order by item_order asc limit 1");
;; if ($con_pk)
;; {
;;     $edit = 1;
;; }
;; else
;; {
;;     $done = 1;
;; }
(defn next_content_page [] 
  (let [result-set (jdbc/execute-one!
                    ds-opts
                    ["select con_pk from content where page_fk=? 
			and valid_content<>0 and item_order>? order by item_order asc limit 1" (:page_pk @params)])]))


(defn item_search []
  (let [result-set (jdbc/execute!
                    ds-opts
                    ["select * from content where page_fk=? order by item_order"
                     (:page_pk @params)])
        ready-data (merge @params {:content result-set})
        html-result (clostache/render (slurp "html/item_search.html") ready-data)]
    (reset! html-out html-result)
    ))

(defn if-page-gen []
  )

(defn if-auto-gen []
  )

;; default is page_search

(def table
  {:page_search
   [[if-edit :edit_page]
    [if-delete :ask_delete_page]
    [if-insert :edit_new_page]
    [#(if-arg :item) :item_search]
    [if-site_gen :site_gen]
    [page_search nil]]

   :edit_page
   [[if-save :save_page]
    [if-continue :save_page_continue]
    [edit_page nil]]

   :save_page
   [[save_page nil]
    [page_search nil]]

   :save_page_continue
   [[save_page nil]
    [edit_page nil]]

   :item_search
   [[if-edit :edit_item]
    [if-page-gen :page_gen]
    [if-auto-gen :auto_gen]
    [item_search nil]]
   })

(comment
  table-part-two
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
   
   :insert_page
   [[insert_page :page_search]]
   :insert_continue
   [[insert_page nil]
    [clear_cont :edit_page]]

   :page_gen
   [[page_gen :item_search]]
   :auto_gen
   [[auto_gen :item_search]]

   :edit_item
   [[if-save :save_item]
    [if-continue :save_item_continue]
    [#(if-arg :next) :edit_next]
    [if-con-pk edit_con_pk]
    [fn-true :item_search]] ;; Falling through to :item_search is strange.

;; 0	edit_item	$save	  save_item()	  item_search
;; 1	edit_item	$continue save_item()	  next
;; 2	edit_item	$continue edit_item()	  wait
;; 3	edit_item	$next	  save_item()	  next
;; 4	edit_item	$next	  next_item()	  next
;; 5    edit_item       $con_pk	  edit_item()	  wait
;; 6 	edit_item	$true 	  null()	  item_search

   :edit_next
   [[save_item nil]
    [next_item nil]
    [edit_item nil]] ;; Assumes we have a good con_pk

   :save_item
   [[save_item :item_search]]

   :save_item_continue
   [[save_item nil]
    [edit_item nil]]

   :edit_con_pk
   [[edit_item nil]]
   })
