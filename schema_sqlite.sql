
-- sqlite3 

-- This is an individual image record. There are many images on one page.
create table "content"	
(
	con_pk integer primary key autoincrement,
	page_fk integer,
	image_name text,
	image_width integer,
	image_height integer,
	description text,
	alt_text text,
	valid_content integer,
	item_order double,
	s_name text,
	s_width integer,
	s_height integer
);

-- insert into content (image_name, description, item_order, page_fk) 
-- (select image_name, description, item_order, (select page_pk from page where page_name=old_content.page_name) from old_content);

-- This is a page of a site. The "site" is implicitly all the pages
-- with the same site name.  image_proto varchar(255),

create table "page"
(
	page_pk integer primary key autoincrement,
	template text,
	menu text,
	page_title text,
	body_title text,
	page_name text,
	search_string text,
	site_name text,
	site_path text,
	owner text,
	page_order double,
	valid_page integer,
	image_dir text,
	external_url text,
	meta_key text, -- keywords meta tag
	meta_desc -- description meta tag
);


