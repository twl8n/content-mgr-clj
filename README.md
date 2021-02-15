#### content-mgr-clj

Photos essay content manager written in Clojure. 

This app is not complete, and only partiallly working. It is being ported from an old Perl app.

#### Usage

`clojure -m cmgr.core`

In a web browser go to: http://localhost:8080/cmgr

#### Requirements

You need a sqlite database named cmgr.db.

`sqlite3 cmgr.db < schema_sqlite.sql`


#### todo

- 2021-02-12 Maybe strip all exif from jpeg images. Converting to pnm and back to jpeg should work.
  jpegtopnm < foo.jpeg -dumpexif -exif=tmp.exif > tmp.pnm
  pnmtojpeg < tmp.pnm > tmp.jpg

- 2021-02-11 Consider adding the top menu to image_t.html aka the big image pages.

- Most (all?) pages with save and save & continue also need "cancel".

- 2021-02-05 Is it really a good idea to conflate "if-" functions and side-effecty functions in the state table?
  It is working, but investigate if the table would be easier to read by adding back in a column for 
  side effecting functions that fire when the "if-" function is true.

(no) 2021-02-05 Make the URL change to match the response. Especially when we're serving static content.
The app's forms are all POST, so this doesn't make sense for that. It might make sense for static content. 

- 2021-02-05 page.site_name and page.site_path are almost always the same. Force them to be the same, and then drop
  one of those fields. Probably keep site_name.

- 2021-02-07 Warn about items with non-unique item_order for a given con_pk. As a fall back, order by item_order,con_pk so
  we at least always have a known order. See content.page_fk=543
  
- 2021-02-07 Remove all embedded HTML from content.description. Come up with work arounds (probably nothing in
  there but paragraph breaks).
  
- 2021-02-07 Move the database into the export directory so that there's nothing sensitive in the git repo
  that might be accidentally checked in.
  
- 2021-02-07 Improve the default export directory config, maybe with a ~/.cmgr or ~/.app_config

- 2021-02-08 Var owner and field page.owner is unused. Clean up.

- 2021-02-08 HTML param ginfo unused. Clean up.

fixed 2021-02-11 menu_gen breaks if there is only a single menu item

fixed 2021-02-08 Var template is unused. Remove template as a var from all code, and set page.template to null.

fixed 2021-02-11 page_search "make site" works. item_search "make page" fails to create the top menu, and may not generate index.html.

fixed cmgr.state/table doesn't need to be an atom. That is a left-over from reading the state table from a file.
  Change it to a normal var.

fixed 2021-02-13 move nav.html out of the git repo, and make it some kind of site-agnostic feature.
  It probably needs to exist in the export directory.

(not a bug) 2021-02-11 image scale in auto_gen needs to deal with both horizontal and vertical images. 
  image_t.html probably also needs work to support horizontal and vertical images.

fixed 2021-02-11 fix item_order in auto_gen to sort the items by their numerical image suffix, and then determine ordinals.
  We don't want IMG_1142.JPG to result in the item_order being 1142.0.

fixed 2021-02-05 In page_search.html we can disable a page, but must use the SQLite CLI to enable a page.
  (Removed unsupported disable feature. Set valid_page to zero to disable a page.)
