#!/bin/bash
set -e
 
VERSION="$1"
DEPLOY="$2"
GH_TOKEN="$3"
TRAVIS_BUILD_NUMBER="$4"


GH_REF="github.com/actiontech/dble-docs-cn"
GH_USER="actiontech-bot"
GH_MAIL="github@actionsky.com"

reg='^[0-9]{1}\.[0-9]{2}\.[0-9]{2}\.doc$' #eg: 2.20.04.doc
if [ "$VERSION" = "master" ]; then
    # pdf
    # xvfb-run gitbook pdf ./ ./dble-manual.pdf
    mkdir -p _book/history/develop
    # merge history—pages
    mkdir _old_book
    cd ./_old_book
    COMMAND_LINE="git clone -b history-pages https://${GH_REF}.git"
    eval "$COMMAND_LINE"
    cd dble-docs-cn
    git archive history-pages | tar -x -C ../../_book/history/
    cd ../..
    # merge develop—pages
    mkdir _develop_book
    cd ./_develop_book
    COMMAND_LINE="git clone -b develop-pages https://${GH_REF}.git"
    eval "$COMMAND_LINE"
    cd dble-docs-cn
    git archive develop-pages | tar -x -C ../../_book/history/develop/ 
    #deploy
    if [ "$DEPLOY" = "1" ]; then
        cd ../../_book
        git init
        git config user.name "${GH_USER}"
        git config user.email "${GH_MAIL}"
        git add .
        git commit -m "Update GitBook By TravisCI With Build $TRAVIS_BUILD_NUMBER"
        git push --force --quiet "https://${GH_TOKEN}@${GH_REF}.git" master:gh-pages 
    fi

elif [ "$VERSION" = "develop" ]; then
  # merge master—pages
    mkdir _master_book
    cd ./_master_book
    COMMAND_LINE="git clone -b gh-pages https://${GH_REF}.git"
    eval "$COMMAND_LINE"
    rm -rf dble-docs-cn/history/develop/
    cd ..
    cp -R _book/ _master_book/dble-docs-cn/history/develop/
    #deploy to develop—pages
    if [ "$DEPLOY" = "1" ]; then
        # push gh-pages
        cd ./_master_book/dble-docs-cn
        git add .
        git commit -m "Update GitBook By TravisCI With Build $TRAVIS_BUILD_NUMBER"
        git push --force --quiet "https://${GH_TOKEN}@${GH_REF}.git" gh-pages:gh-pages
        # push develop—pages
        cd ../../_book
        git init
        git config user.name "${GH_USER}"
        git config user.email "${GH_MAIL}"
        git add .
        git commit -m "Update GitBook By TravisCI With Build $TRAVIS_BUILD_NUMBER"
        git push --force --quiet "https://${GH_TOKEN}@${GH_REF}.git" master:develop-pages
    fi
elif [[ "$VERSION" =~ $reg ]]; then
    mkdir _old_book
    cd ./_old_book
    COMMAND_LINE="git clone -b history-pages https://${GH_REF}.git"
    eval "$COMMAND_LINE"
    rm -rf dble-docs-cn/$VERSION
    cd ..
    cp -R _book/ _old_book/dble-docs-cn/$VERSION
    cd _old_book/dble-docs-cn/$VERSION
    rm -rf .gitignore .travis.yml deploy.sh package-lock.json

    cd ../../..
    mkdir _master_book
    cd ./_master_book
    COMMAND_LINE="git clone -b gh-pages https://${GH_REF}.git"
    eval "$COMMAND_LINE"
    rm -rf dble-docs-cn/history/$VERSION/
    cd ..
    cp -R _book/ _master_book/dble-docs-cn/history/$VERSION/
    cd _master_book/dble-docs-cn/history/$VERSION/
    rm -rf .gitignore .travis.yml deploy.sh package-lock.json
    cd ../../../..

    ## push
    if [ "$DEPLOY" = "1" ]; then
        cd ./_old_book/dble-docs-cn ##history-pages
        git add .
        git commit -m "Update GitBook By TravisCI With Build $TRAVIS_BUILD_NUMBER"
        git push --force --quiet "https://${GH_TOKEN}@${GH_REF}.git" history-pages:history-pages
        cd ../../
        cd ./_master_book/dble-docs-cn  ##gh-pages
        git add .
        git commit -m "Update GitBook By TravisCI With Build $TRAVIS_BUILD_NUMBER"
        git push --force --quiet "https://${GH_TOKEN}@${GH_REF}.git" gh-pages:gh-pages
    fi
else
    echo "do nothing"
fi
