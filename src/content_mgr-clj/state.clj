(ns machine.state
  (:require [clojure.string :as str]
            ;; [clojure.java.jdbc :as jdbc]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clostache.parser :as clostache] ;; [clostache.parser :refer [render]]
            ;; [hugsql.core :as hugsql]
            [clojure.pprint :as pp]))

;; I think db is a "connection"
(def db {:dbtype "sqlite" :dbname "cmgr.db"})

;; 2021-01-31 We could use rs/as-unqualified-lower-maps, but since we're using lowercase in our schema,
;; and since we're used to sql drivers returning the case shown in the schema (or uppercase) we don't have to
;; force everything to lower at this time.

;; with-options works on db or ds
;; I guess ds-opts is a "datasource with options" in next.jdbc parlance.
(def ds-opts (jdbc/with-options db {:return-keys true :builder-fn rs/as-unqualified-maps}))

(comment
  ;; Simpler because it relies on ds-opts having with-options
  (def foo (jdbc/execute! ds-opts
                          ["select * from page where site_name=? limit 3" "hondavfr"]))

  ;; alternate stuff that works
  ;; https://github.com/seancorfield/next-jdbc/blob/develop/doc/getting-started.md

  ;; ds is clearly a "datasource"
  (def ds (jdbc/get-datasource db))
  (def foo (jdbc/execute! ds ["select * from page limit 1"]))

  (def foo (jdbc/execute! ds
                          ["select * from page limit 2"]
                          {:return-keys true :builder-fn rs/as-unqualified-maps}))

  (def foo (jdbc/execute! ds
                          ["select * from page where site_name=? limit 1" "hondavfr"] 
                          {:return-keys true :builder-fn rs/as-unqualified-maps}))

  (def foo (jdbc/execute! ds
                          ["select * from page where site_name=? limit 1" "hondavfr"] 
                          {:return-keys true :builder-fn rs/as-unqualified-maps}))
  )



(defn msg [arg] (printf "%s\n" arg))

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

(defn if-edit [] )
(defn if-delete [] )
(defn if-insert [] )
(defn if-item [])
(defn if-site_gen [] )

(defn page_search []
  ;; keep("findme,findme_encoded");
  ;; $findme = CGI::unescape($findme);
  ;; if ($findme_encoded)
  ;; {
  ;;     $findme = www_dequote($findme_encoded);
  ;; }
  ;; $owner = `/usr/bin/id -un`;
  ;; chomp($owner);
  ;; do_sql_simple("hondavfr","","select * from page where owner='\$owner'");
  ;; #default, fields to search, found records field
  
  ;; do_search() limits the rows we keep. This probably excludes valid_page=0 aka invalid pages.
  ;; my $findme_col = $_[0]; # col to search
  ;; my $default_findme = $_[1]; # string to search for
  ;; my $def_str = $_[2];  # ??
  ;; my $rank = $_[3]; # name of the ranking field, aka the found record flag
  ;; my $extras_list = $_[4]; # s, es, recordsfound
  ;; do_search("valid_page:1 valid_page:0", "page_pk,valid_page,page_title,body_title,page_name,site_name", "rank");
  
  ;; dcc("dcc_site", "site_name", [""], ["site_name,at"]);
  ;; dcc("dcc_page", "page_pk", ["rank >= 1"],["page_order,an"]);
  ;; $findme_encoded = www_quote($findme);
  ;; render("","Content-type: text/html\n\n","page_search.html", "");

  
  (let [params {}
        result-set (jdbc/execute! ds-opts
                                  ["select * from page where valid_page=1"])]
    
    (clostache/render (assoc params
                       :sys-msg "trying all-language"
                       :dcc_site (group-by (juxt :site_name :page_pk) result-set))
                      (slurp "resources/html/list.html"))
    )
  )

(comment
  (def foo
    (let [result-set (jdbc/execute! ds-opts
                                    ["select * from page where valid_page=1 limit 2"])
          ready-data
          (merge {:recordsfound 1
                  :s ""
                  :template "page_search.html"
                  :_d_state "d state"
                  :findme_encoded "foo"}
                 {:dcc_site (mapv (fn [xx] {:site_name (key xx)
                                           :dcc_page (val xx)}) (group-by :site_name result-set))})]
      ;; (group-by (juxt :site_name :page_pk) result-set)
      ;; (group-by :site_name result-set)
      (spit "tmp.html"
      (clostache/render
       (slurp "html/page_search.html")
       ready-data))
      (def rd ready-data)
      )
    )

(def bar (jdbc/execute! ds-opts ["select * from page where valid_page=1 limit 2"]))
(group-by  (fn [xx] {:site_name (:site_name xx) :dcc_page xx}) bar)

(clostache/render
   "stuff {{#dcc_site}} site_name: {{site_name}} {{#dcc_page}} page_name: {{page_name}} {{/dcc_page}} {{/dcc_site}} end"
   {:dcc_site [{:site_name "hondavfr" :dcc_page [{:page_name "intro"}{:page_name "cases"}]} {:site_name "bug"}]})

(clostache/render
 "stuff {{#dcc_site}} site_name: {{site_name}} {{#dcc_page}} page_name: {{page_name}} {{/dcc_page}} {{/dcc_site}} end"
 rd)
   {:recordsfound 1 :dcc_site (map (fn [xx] {:site_name (key xx) :dcc_page (val xx)}) (group-by :site_name bar))})


  (def foo 
    [["r850r" "aa" "fuel_pump_connector.html" 4219 8.1]
     ["r850r" "aa" "review.html" 4233 9.0]
     ["sorghum" "bb" "index.html" 4287 1.0]
     ["trucks" "cc" "index.html" 4280 1.0]
     ["trucks" "cc" "bench_seat.html" 4281 2.0]
     ["trucks" "cc" "f250_window.html" 4282 3.0]
     ["volkswagen" "dd" "index.html" 3982 1.0]
     ["volkswagen" "dd" "golf_headlight.html" 3996 2.0]
     [ "volkswagen" "dd" "golf_battery.html" 4271 3.0]])

  ;; Create a list of hashes where keys are the field names
  (def bar (map #(apply zipmap [[:site_name :x_var :page_name :page_pk :page_order] %]) foo))

  ;; Create a list of lists [[site1 [pages]] [site2 [pages]] ...]
  (group-by (juxt :site_name :x_var) bar)
  )

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
    [[save_page :page_search]]

    :save_page_continue
    [[save_page nil]
     [edit_page :edit_page]]

    :save_next
    [[save_page nil]
     [next_page :edit_page]]

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

