(ns cmgr.state
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clostache.parser :as clostache] ;; [clostache.parser :refer [render]]
            [clojure.pprint :as pp]))

(def config (atom {}))

;; This won't work until cmgr.core has finished compiling.
(when (resolve 'cmgr.core/init-config) ((eval (resolve 'cmgr.core/init-config))))

(defn set-config [new-config]
  (reset! config new-config))
  

(def html-out (atom ""))

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

;; (if-arg :item)
(defn if-arg [tkey]
  (let [tval (tkey @params)
        ret (and (seq tval) tval)]
    (swap! params #(dissoc % tkey))
    (boolean ret)))

(defn if-save []
  (let [ret (= "Save" (:save @params))]
    (swap! params #(dissoc % :save))
    ret))

(defn if-continue []
  (let [ret (= "Save & Continue" (:continue @params))]
    (swap! params #(dissoc % :continue))
    ret))
        
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

(defn edit_page []
  (let [result-set (jdbc/execute-one! ds-opts ["select * from page where page_pk=?" (:page_pk @params)])
        [ext_one ext_zero] (if (= 1 (:external_url @params)) ["selected" ""] ["" "selected"])
        ready-data (merge result-set
                          {:d_state "edit_page"
                           :ext_one ext_one
                           :ext_zero ext_zero}
                          )
        html-result (clostache/render (slurp "html/edit_page.html") ready-data)]
    (reset! html-out html-result)))

(defn save_page []
  (let [result-set (sql/update! ds-opts
                                :page
                                (select-keys @params
                                             [:menu :page_title :body_title
                                              :page_name :search_string :image_dir
                                              :site_name :site_path :page_order
                                              :valid_page :external_url :page_pk])
                                {:page_pk (:page_pk @params)})]
    true))

(defn next_content_page [] 
  ;; do_sql_simple("hondavfr", "", "select con_pk from content where page_fk=\$page_pk and valid_content<>0 and item_order>\$item_order order by item_order asc limit 1");
  ;; if ($con_pk)
  ;; {
  ;;     $edit = 1;
  ;; }
  ;; else
  ;; {
  ;;     $done = 1;
  ;; }
  (let [result-set (jdbc/execute-one!
                    ds-opts
                    ["select con_pk from content where page_fk=? 
			and valid_content<>0 and item_order>? order by item_order asc limit 1" (:page_pk @params)])]))

;; item = photo+text
;; page = one or more items
;; item_search is a sort-of preview-ish list of all the items for a given page.
;; It has a button on each item preview to edit that item.
(defn item_search []
  (let [raw-set (jdbc/execute!
                    ds-opts
                    ["select content.*, page.site_name, page.image_dir, page.site_path 
			from content,page where page_fk=? and page_pk=page_fk order by item_order"
                     (:page_pk @params)])
        ;; Need to html-encode :description THEN regex the \n and \r
        ;; I see a \$ in con_pk 2799
        result-set (mapv #(assoc % :desc-html (str/replace (or (:description % "") "") #"([\000-\037]+)" "<br><br>")) raw-set)
        ready-data (merge
                    @params
                    {:site_name (:site_name (first raw-set))}
                    {:content result-set :d_state "item_search"})
        html-result (clostache/render (slurp "html/item_search.html") ready-data)]
    (reset! html-out html-result)
    ))

;; Historically, we converted any 4 control characters (newlines) convert to <br><br> tags 
;; when saving, and converted the other way when reading from the db and rendering in HTML.
;; Bad idea. Just leave them as newlines and convert only when necessary for html, which is not in a <textarea> tag.
;; (let [desc (str/replace (:description @params) #"([\000-\037]{4})" "<br><br>")]
;;   (swap! params #(assoc % :description desc))

;; I think we aren't saving the other fields because they were fixed when the item was created, and cannot
;; be changed.
(defn save_item []
  (let [result-set (sql/update! ds-opts
                                :content
                                (select-keys @params
                                             [:description :alt_text :item_order :valid_content])
                                {:page_fk (:page_fk @params)
                                 :con_pk (:con_pk @params)})]
    true)
  )

;; 2021-02-08 This will re-edit the same item if there is no next, and that's a bit confusing.
(defn next_item []
  (let [old_con_pk (:con_pk @params)
        result-set (jdbc/execute-one!
                    ds-opts
                    ["select con_pk from content 
			where page_fk=? and 
			valid_content=1 and item_order>? 
			order by item_order,con_pk limit 1"
                     (:page_pk @params) (:item_order @params)])]
    (swap! params #(assoc % :con_pk (:con_pk result-set old_con_pk)))))


;; The old Perl code that attempted to resize the textarea based on the amount of text.
;; $drows = p_count($description); # number of <p> or \n in $description
;; $drows += (length($description)/60);
;; $drows += ($drows*0.3);
;; $drows = sprintf("%d", $drows);
(defn edit_item []
  (let [result-set (jdbc/execute-one!
                    ds-opts
                    ["select page_fk,
		      	image_name, image_width, image_height,
		      	description, alt_text, valid_content,
		      	item_order, s_name, s_width, s_height,
		      	menu, page_title, body_title,
		      	page_name, search_string, site_name, site_path,
		      	page_order, valid_page, image_dir, external_url
		      	from content,page where con_pk=? and page_fk=page_pk"
                     (:con_pk @params)])
        ready-data (merge @params
                          result-set
                          {:drows 20
                           :page_pk (:page_fk result-set)
                           :d_state "edit_item"})
        html-result (clostache/render (slurp "html/edit_item.html") ready-data)]
    (reset! html-out html-result)
    ))

(defn menu_gen [site_name]
  (let [;; site_name "hondavfr"
        result-set (jdbc/execute!
                    ds-opts
                    ["select page_pk as menu_page_pk,page_order,page_name,menu,site_path from page where
			site_name=? and valid_page<>0 order by page_order" site_name])
        ordinal-set (map-indexed #(assoc %2 :ordinal  %1) result-set)
        desired-columns 5.0
        ;; Use quot to get an integer number of lines from a rounded-up float number of lines.
        ;; Require at least desired-columns as a min that yields 1 line.
        number-of-lines (quot (+ 0.5 (/ (max desired-columns (count ordinal-set)) desired-columns)) 1)  ;; 3 for hondavfr
        pb-set (mapv #(assoc % :page_break (mod (:ordinal %) number-of-lines)) ordinal-set)
        ready-data (assoc {} :menu_line (mapv (fn [xx] {:first_col (val xx)}) (group-by :page_break pb-set)))
        ]
    ;; returns an html fragment
    (clostache/render (slurp "html/menu_template.html") ready-data)))

(comment
  ;; page_pk 543 ;; hondavfr page with lots of images
  (gen_single_page 543 (menu_gen "hondavfr"))
  )

;; Are these item pages or image pages? Ideally, we'd be consistent about the name.
(defn gen_image_pages [ready-data]
  (doseq [page-data (:content-ordinal ready-data)]
    (let [page_pk (:page_pk ready-data)
          item_order (:item_order page-data)
          page_stem (:page_stem ready-data)
          site_path (:site_path ready-data)
          max_ordinal (:max_ordinal (jdbc/execute-one!
                                     ds-opts
                                     ["select count(*) as max_ordinal from content where valid_content=1 and page_fk=?" page_pk]))
          prev (:prev (jdbc/execute-one!
                       ds-opts
                       ["select count(*) as prev from content 
			where valid_content=1 and page_fk=? and item_order<?" page_pk item_order]))
          prev_flag (< 0 prev)
          ordinal (+ 1 prev)
          next (+ 2 prev)
          next_flag (<= next max_ordinal)
          prev-name (format "%s_%s_i.html" page_stem prev)
          next-name (format "%s_%s_i.html" page_stem next)
          ;; full-site-path (format "%s/%s" (:export-path @config) (:site_path page-rec))       
          full_page_name (format "%s/%s/%s_%s_i.html" (:export-path @config) site_path page_stem ordinal)
          html-fragment (clostache/render (slurp "html/image_t.html")
                                          (merge page-data
                                                 ready-data
                                                 {:ordinal ordinal
                                                  :next next_flag
                                                  :next-name next-name
                                                  :prev prev_flag
                                                  :prev-name prev-name}))]
      (spit full_page_name html-fragment)
      )))


;; A "site" is all the pages with the same site_name.
(comment
  (cmgr.core/init-config)
  (do (reset! params {:site_name "hondavfr"})
      (site_gen)
      (reset! params {:site_name "hondavlx"})
      (site_gen))
  )


;; file:///Users/twl/Sites/content-manager-pages/hondavfr/givi_cases_vfr/givi_cases_vfr_16_s.jpg
(defn gen_single_page [page_pk menu_text]
  (let [nav (slurp "html/nav.html")
        page-rec (jdbc/execute-one!
                  ds-opts
                  ["select
			page_pk, menu, page_title, body_title, page_name,
			search_string, image_dir, site_name, site_path,
			page_order, valid_page,external_url
			from page where page_pk=?" page_pk])
        page_stem (second (re-matches #"(.*)\..*" (:page_name page-rec)))
        content-recs (jdbc/execute!
                      ds-opts
                      ["select con_pk,image_height,description,alt_text,valid_content,s_name,s_width,s_height,
			image_name,image_width,item_order
			from content where page_fk=? and valid_content=1 order by item_order, con_pk" page_pk])
        ;; For :flag I'm pretty sure we want the first record which is ordinal (index) zero.
        content-ordinal (map-indexed #(assoc %2 :ordinal  (inc %1)
                                             :flag (= (inc %1) 1)
                                             :pf_name (format "%s_%s_i.html" page_stem (inc %1))) content-recs)
        ;; dcc("dcc_start_outer", "page_fk", ["item_ordinal < 2"], []);
        ;; dcc_start_outer (filter :flag content-ordinal)
        ;; dcc("dcc_start_inner", "con_pk", ["item_ordinal < 2"], ["item_ordinal,an"]);
        start_inner (filter :flag content-ordinal)
        ;; dcc("dcc_remainder", "con_pk", ["item_ordinal >= 2"], ["item_ordinal,an"]);
        remainder (vec (remove :flag content-ordinal))

        full-site-path (format "%s/%s" (:export-path @config) (:site_path page-rec))
        full_page_name (format "%s/%s" full-site-path (:page_name page-rec))
        ;;     $path_exists = test_path($site_path);
        ready-data (assoc page-rec
                          :nav nav
                          :page_stem page_stem
                          :menu_text menu_text
                          :full_page_name full_page_name
                          :content-ordinal content-ordinal
                          :start_inner start_inner
                          :remainder remainder)
        html-fragment (clostache/render (slurp "html/main_t.html")
                                        ready-data)]
    (spit full_page_name html-fragment)
    (gen_image_pages ready-data)
    ))

(defn site_gen []
  (let [site_name (:site_name @params)
        result-set (jdbc/execute!
                    ds-opts
                    ["select page_pk from page where
	 	     site_name=? and valid_page<>0 and external_url=0" site_name])
        menu-html (menu_gen site_name)]
    ;; Create export html file for each page.
    (doseq [single-page result-set]
      (gen_single_page (:page_pk single-page) menu-html))))


(defn insert_page []
  (let [site_path (:site_path @params)
        image_dir (:image_dir @params)
        export-path (:export-path @config)]
    (.mkdirs (io/file (format "%s/%s/images/%s" export-path site_path image_dir)))
    (sql/insert! ds-opts :page (select-keys @params [:menu :page_title :body_title :page_name :search_string :image_dir :site_name :site_path :page_order :valid_page :external_url]))))


(defn clear_continue []
 (swap! params #(dissoc % :continue)))


(defn edit_new_page []
  (let [html-result (clostache/render (slurp "html/edit_page.html")
                                      {:d_state "edit_new_page"
                                       :menu ""
                                       :page_title ""
                                       :body_title ""
                                       :page_name ""
                                       :search_string ""
                                       :image_dir ""
                                       :site_name (:site_name @params "") ;; Add new page from existing site or brand new.
                                       :site_path ""
                                       :page_order 0.0
                                       :valid_page 0
                                       :external_url 0
                                       :ext_zero "selected"
                                       :ext_one ""})]
    (reset! html-out html-result)))

(defn page_gen []
  (gen_single_page (:page_pk @params) (menu_gen (:site_name @params))))

(defn delete_page []
  (println "fn delete_page does nothing.")
  )

(defn get_wh [full_name]
  (let [[_ width height] (re-matches  #"(?s)(\d+)\s+(\d+).*"
                                      (:out (shell/sh "sh" "-c" (format "jpegtopnm < %s| pnmfile -size" full_name))))]
    [(Integer. width) (Integer. height)]))

(comment
  (let [xsize 320
        full_name "/Users/twl/Sites/content-manager-pages/1990_f250/images/f250/IMG_1142.JPG"
        full_s_name "/Users/twl/Sites/content-manager-pages/1990_f250/images/f250/IMG_1142_s.JPG"
        cmd (format "jpegtopnm < %s | pnmscale -xsize=%s -verbose | pnmtojpeg > %s 2>&1" full_name xsize full_s_name)]
        (shell/sh "sh" "-c" cmd))


  (cmgr.core/init-config)
  (def foo (.list (io/file "/Users/twl/Sites/content-manager-pages/1990_f250/images/f250/")))
  (filter #(re-find #"(?i).*\.jp.*g" %) foo) 

  (do (reset! params {:findme ""
                      :d_state :item_search
                      :page_pk 4288
                      :auto_gen "Auto Gen Items"})
      (auto_gen))
      )

;; Important that file-list is only good jpeg files with names matching #"(?i).*_\d+\.jp.*g"
;; Do not process foo_123_s.jpg files or any non-jpeg files.
(defn auto_gen []
  (let [page_pk (:page_pk @params)
        {:keys [site_path image_dir]} (jdbc/execute-one!
                                       ds-opts
                                       ["select site_path, image_dir from page where page_pk=?" page_pk])
        file-list (filter #(re-find #"(?i).*_\d+\.jp.*g" %)
                          (.list (io/file (format "%s/%s/images/%s" (:export-path @config) site_path image_dir))))]
    (doseq [short_name file-list]
      (let [full_name (format "%s/%s/images/%s/%s" (:export-path @config) site_path image_dir short_name)
            full_s_name (str/replace full_name #"(?i)\.jp.*g" "_s.jpg")
            image_name (str/replace full_name #".*\/(.*)" "$1")
            xsize 320
            cmd (format "jpegtopnm < %s | pnmscale -xsize=%s | pnmtojpeg > %s 2>&1" full_name xsize full_s_name)
            conv_results
            (shell/sh "sh" "-c" cmd)
            [image_width image_height] (get_wh full_name)
            [s_width s_height] (get_wh full_s_name)
            {:keys [exists]} (jdbc/execute-one!
                              ds-opts
                              ["select (count(*)>0) as 'exists' from content where image_name=? and page_fk=?"
                               image_name page_pk])
            item_order (Integer. (str/replace full_name #"(?i).*\/.*_(\d+).jp.*g" "$1"))
            s_name (str/replace full_s_name #".*\/(.*)" "$1")
            insert-data {:page_fk page_pk
                         :image_name image_name
                         :image_width image_width
                         :image_height image_height
                         :valid_content 1
                         :description "enter a description"
                         :item_order item_order
                         :s_name s_name
                         :s_width s_width
                         :s_height s_height}]
        (when (= 0 exists)
          (sql/insert! ds-opts :content insert-data))
        ))))
        

;; Default is page_search.
;; This is a less than ideal state table because we retain the d_state (e.g. :page_search) between invocations.
;; We should be saving some "state" information and always running the state machine from the same starting point.
;; A content manager web app can afford to bend the rules, and time will tell if we've created a buggy mess.

(comment
  ;; This sort of describes the map of lists of lists that is the state table.
  {:starting-default-state
   [[if-test :other-state]
    [side-effect-fn-a nil]]
   :other-state
   [[site-effect-fn-b nil]
    [render-html-change-state nil]]}
  )

(def table
  {:page_search
   [[if-edit :edit_page]
    [#(if-arg :delete) :ask_delete_page]
    [#(if-arg :insert) :edit_new_page]
    [#(if-arg :item) :item_search]
    [#(if-arg :site_gen) :site_gen]
    [page_search nil]]

   :site_gen
   [[site_gen nil]
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
    [#(if-arg :page_gen) :page_gen]
    [#(if-arg :auto_gen) :auto_gen]
    [item_search nil]]

   :page_gen
   [[page_gen nil]
    [item_search nil]]

   :edit_item
   [[if-save :save_item]
    [if-continue :save_item_continue]
    [#(if-arg :next) :edit_next]
    [edit_item nil]]

   :edit_next
   [[save_item nil]
    [next_item nil]
    [edit_item nil]] ;; Assumes we have a good con_pk

   :save_item
   [[save_item nil]
    [item_search nil]]

   :save_item_continue
   [[save_item nil]
    [edit_item nil]]

   :edit_new_page
   [[if-save :insert_page]
    [if-continue :insert_continue]
    [edit_new_page nil]]
   
   :insert_page
   [[insert_page :page_search]]
   :insert_continue
   [[insert_page nil]
    [clear_continue :edit_page]]

   :ask_del_page
   [[#(if-arg :confirm) :delete_page]
    [page_search nil]]
   ;; 0	ask_del_page	$confirm  delete_page()	  page_search
   ;; 1	ask_del_page	$true	  ask_del_page()  wait

   :delete_page
   [[delete_page nil]
    [page_search nil]]

   :auto_gen
   [[auto_gen :item_search]]
   })

