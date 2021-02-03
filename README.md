#### content-mgr-clj

Photos essay content manager written in Clojure. 

This app is not complete, and only partiallly working. It is being ported from an old Perl app.

#### Usage

`clojure -m cmgr.core`

In a web browser go to: http://localhost:8080/app

#### Requirements

You need a sqlite database named cmgr.db.

`sqlite3 cmgr.db < schema_sqlite.sql`


#### todo

x cmgr.state/table doesn't need to be an atom. That is a left-over from reading the state table from a file.
  Change it to a normal var.

- Most (all?) pages with save and save & continue also need "cancel".
  
