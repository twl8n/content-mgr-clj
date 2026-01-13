#### content-mgr-clj

Photos essay content manager written in Clojure. 

This app is used to edit content and create static HTML photo essay web sites. Here is an example:

http://laudeman.com/bmw_r850r/index.html

#### Usage

At the command line:

`clj -X cmgr.core/-main`

or:

`clojure -X cmgr.core/-main`

The program runs, and your default web browser will open with:

`http://localhost:8080/cmgr`

This works for me on MacOS 13.7.7. If the web browser doesn't open automatically, you'll have to do it yourself, and visit the URL above.


todo: See https://clojure.org/guides/deps_and_cli#_using_git_libraries

#### Requirements

SQLite https://sqlite.org/download.html
NetPBM http://netpbm.sourceforge.net/
Clojure https://clojure.org/guides/getting_started


In your home directory, create a .cmgr file. The key export-path is a full, absolute path to the working directory. The key db-path is a full, absolute path to the database file.

```
;; Config file for Tom's content manager https://github.com/twl8n/content-mgr-clj
export-path /Users/zeus/Sites/content-manager-pages
db-path /Users/zeus/cmgr.db
```

You need a sqlite database named cmgr.db. Create it in a directory that is not the app source, and not the
export directory. Put the db file path in .cmgr config file.

`sqlite3 cmgr.db < schema_sqlite.sql`


In that directory, each "site" has a directory. Inside the site directory must be a directory "images" which
is shared by all pages within that site. Under images are directories for each "page" in the site. You must
create a "nav.html" left page navigation HTML snippet file in the export directory. Yes, you manually create a
file containing an HTML snippet. Base your navigation on ./html/nav-demo.html


```
export-path
          |-site-one
          |-site-two
          |-nav.html
          |-images
                 |-page-one
                          |-img_1.jpeg
                          |-img_2.jpeg
                 |-page-two
                          |-img_03.jpg
                          |-img_04.jpg
```

You must plan your site, create the directory structure, and copy jpeg images into the page subdirectories.
The jpeg image file names must have a specific suffix: _n.jpg where n is an integer (with optional leading
zero). For example: img_1.jpg

In the application, you must create sites and pages to match the directory tree you've created. The app will
auto create new (blank) items for each image within a given page.

Once items are created, use the app to add a text description for each image. Image may be reordered.

The "site gen" and "page gen" features will generate sites
and pages, including menus. All the static html files are
created at the top level, so you must choose unique page
names.

Rsync each site to the server. Here's an example rsync command:

```
cd ~/myexport
rsync -azvP --delete site-one myserver:public_html/myboringlife/
```



#### todo

- 2026-01-11 Lost the site name/description/title. Need to add it back. 

- 2026-01-10 Move image_t navigation to the top of the page. Unclear why it has been at the bottom?

x 2026-01-10 Change nav-demo.html and nav.html to modern hamburger mobile menu

- 2026-01-10 What is:
> clojure -m cmgr.core
WARNING: Implicit use of clojure.main with options is deprecated, use -M -m cmgr.core


- 2026-01-10 Try to move 404 and other error processing inside the state machine.

- 2026-01-10 Need more docs on each part of the state machine hash and args, which should be easy since I've
  forgotten how it all works.

+ 2026-01-10 Open the home page when the app launches.

- 2026-01-10 java.lang.NullPointerException: Response map is nil or when the state machine has a problem.
 Basically, a nil response map should never occur, but we need to handle it if it does. When the response map
 is nil, load the home page http://localhost:8080/cmgr

- 2024-11-07 Feature, correctly handle vertical images.

- (done) 2024-11-06 Add database path to .cmgr. 

- (done) 2024-11-06 Ignore favicon.

- 2021-03-10 Generalize the Babashka script I use for rsync, add to this repo, write instructions.

- 2021-03-01 Add a "build all" button.

- 2021-03-01 Save and Edit button on edit_new_page to save and go to the edit content (item_search.html)
Maybe also print some hints about what images path to use with mkdir.

- 2021-02-27 Is machine.core a necessary requirement in core.clj? In state.clj?

- 2021-02-12 Maybe strip all exif from jpeg images. Converting to pnm and back to jpeg should work.
  jpegtopnm < foo.jpeg -dumpexif -exif=tmp.exif > tmp.pnm
  pnmtojpeg < tmp.pnm > tmp.jpg

- 2021-02-11 Consider adding the top menu to image_t.html aka the big image pages.

- 2021-02-19 Most (all?) pages with save and save & continue also need "cancel".

- 2021-02-05 Is it really a good idea to conflate "if-" functions and side-effecty functions in the state table?
  It is working, but investigate if the table would be easier to read by adding back in a column for 
  side effecting functions that fire when the "if-" function is true.

- 2021-02-05 page.site_name and page.site_path are almost always the same. Force them to be the same, and then drop
  one of those fields. Probably keep site_name.

- 2021-02-07 Warn about items with non-unique item_order for a given con_pk. As a fall back, order by item_order,con_pk so
  we at least always have a known order. See content.page_fk=543
  
- 2021-02-07 Remove all embedded HTML from content.description. Come up with work arounds (probably nothing in
  there but paragraph breaks).
  
- 2021-02-07 Move the database into the export directory so that there's nothing sensitive in the git repo
  that might be accidentally checked in.
  
- 2021-02-08 Var owner and field page.owner is unused. Clean up.

(done) 2021-02-27 Use github.com/twl8n/machine as the state machine module. Remove all code that isn't part of
the content manager from this repo.

(done) 2021-02-07 Create an export directory config file ~/.cmgr

(done) 2021-02-19 Add an optional third arg to state table first column. Allow if-arg tests to run an anonymous function.

(no) 2021-02-05 Make the URL change to match the response. Especially when we're serving static content.
The app's forms are all POST, so this doesn't make sense for that. It might make sense for static content. 


(fixed) 2021-02-15 expunge findme* ginfo and search

(fixed) 2021-02-08 HTML param ginfo unused. Clean up.

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
