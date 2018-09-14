default: gitbook_build publish
publish: publish_prepare publish_push

gitbook_preview:
	gitbook serve
gitbook_build:
	gitbook build

pdf:
	gitbook pdf ./ ./dble-manual.pdf

install:
	npm install -g gitbook-cli
	gitbook install

publish_prepare:
	git checkout gh-pages
	git pull origin gh-pages --rebase
	cp -R _book/* .
	git clean -fx node_modules
	git clean -fx _book
	git add .
	git commit -a -m "Update docs"

publish_push:
	git push origin gh-pages
	git checkout master

merge_book:
	mkdir tmp
	mkdir tmp/pic 
	#readme
	cp README.md tmp/0.0.README.md
	# charter0
	cat 0.overview/*.md > tmp/0.overview.md
	cp  0.overview/pic/* tmp/pic/
	# charter1
	cat 1.config_file/1.6_cache/*.md > 1.config_file/1.6_cache.md.detail.md
	cat 1.config_file/1.7_global_sequence/*.md > 1.config_file/1.7_global_sequence.md.detail.md

	cat 1.config_file/*.md > tmp/1.config_file.md
	# charter2
	cat 2.Function/2.1_manager_cmd/*.md > 2.Function/2.01_manager_cmd.md.detail.md
	cat 2.Function/2.2_global_sequence/*.md > 2.Function/2.02_global_sequence.md.detail.md
	cat 2.Function/2.5_distribute_transaction/*.md > 2.Function/2.05_distribute_transaction.md.detail.md
	cat 2.Function/2.10_table_meta/*.md > 2.Function/2.10_table_meta.md.detail.md
	cat 2.Function/2.11_statistics_manager/*.md > 2.Function/2.11_statistics_manager.md.detail.md

	cat 2.Function/*.md > tmp/2.Function.md
	cp 2.Function/pic/* tmp/pic/
	cp 2.Function/2.5_distribute_transaction/pic/* tmp/pic/
	cp 2.Function/2.10_table_meta/pic/* tmp/pic/
	# charter3
	cat 3.SQL_Syntax/3.1_DDL/*.md > 3.SQL_Syntax/3.1_DDL.md.detail.md
	cat 3.SQL_Syntax/3.2_DML/*.md > 3.SQL_Syntax/3.2_DML.md.detail.md
	cat 3.SQL_Syntax/3.4_Transactional_and_Locking_Statements/*.md > 3.SQL_Syntax/3.4_Transactional_and_Locking_Statements.md.detail.md
	cat 3.SQL_Syntax/3.5_DAL/*.md > 3.SQL_Syntax/3.5_DAL.md.detail.md

	cat 3.SQL_Syntax/*.md > tmp/3.SQL_Syntax.md

	# charter4
	cat 4.Protocol/*.md > tmp/4.Protocol.md
	# charter5
	cat 5.Limit/*.md > tmp/5.Limit.md
	# charter6
	cat 6.Differernce_from_MySQL_Server/*.md > tmp/6.Differernce_from_MySQL_Server.md
	# charter7
	cat 7.Developer_Notice/*.md > tmp/7.Developer_Notice.md
	# all
	cat tmp/*.md > tmp/dble-doc-cn.md
