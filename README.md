#### content-mgr-clj

Photos essay content manager written in Clojure. 

This app is not complete, and only partiallly working. It is being ported from an old Perl app.

#### Usage

`clojure -m cmgr.core`

In a web browser go to: http://localhost:8080/

#### Requirements

You need a sqlite database named cmgr.db.

`sqlite3 cmgr.db < schema_sqlite.sql`


#### todo

x cmgr.state/table doesn't need to be an atom. That is a left-over from reading the state table from a file.
  Change it to a normal var.

- Most (all?) pages with save and save & continue also need "cancel".

- 2021-02-05 In page_search.html we can disable a page, but must use the SQLite CLI to enable a page.
  
- 2021-02-05 Is it really a good idea to conflate "if-" functions and side-effecty functions in the state table?
  It is working, but investigate if the table would be easier to read by adding back in a column for 
  side effecting functions that fire when the "if-" function is true.

- 2021-02-05 Make the URL change to match the response. Especially when we're serving static content.

- 2021-02-05 page.site_name and page.site_path are almost always the same. Force them to be the same, and then drop
  one of those fields. Probably keep site_name.

- 2021-02-07 Warn about items with non-unique item_order for a given con_pk. As a fall back, order by item_order,con_pk so
  we at least always have a known order. See content.page_fk=543
  
- 2021-02-07 Remove all embedded HTML from content.description. Come up with work arounds (probably nothing in
  there but paragraph breaks).
  
- 2021-02-07 Move the database into the export directory so that there's nothing sensitive in the git repo
  that might be accidentally checked in.
  
- 2021-02-07 Improve the default export directory config, maybe with a ~/.cmgr or ~/.app_config

x 2021-02-08 Var template is unused. Remove template as a var from all code, and set page.template to null.

- 2021-02-08 Var owner and field page.owner is unused. Clean up.

- 2021-02-08 HTML param ginfo unused. Clean up.
