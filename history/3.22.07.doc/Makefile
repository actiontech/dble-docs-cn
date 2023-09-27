default: pull_code  gitbook_install gitbook_build  gitbook_pdf

gitbook_preview:
	docker run --rm -v "${PWD}":/gitbook -p 4000:4000 billryan/gitbook:zh-hans gitbook serve
gitbook_install:
	docker run --rm -v "${PWD}":/gitbook -p 4000:4000 billryan/gitbook:zh-hans gitbook install
gitbook_build:
	docker run --rm -v "${PWD}":/gitbook -p 4000:4000 billryan/gitbook:zh-hans gitbook build
pull_code:
	git pull origin master --rebase
gitbook_pdf:
	docker run --rm -v "${PWD}":/gitbook -p 4000:4000 billryan/gitbook:zh-hans gitbook pdf ./ ./dble-manual.pdf
	

