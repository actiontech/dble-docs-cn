#!/bin/bash
if [ ! -d "/root/.fonts" ] ; then
    mkdir /root/.fonts
    echo -e "create ~/.fonts/ \n"
fi

# Download

wget -O SourceHanSansHWSC.zip -nv https://github.com/adobe-fonts/source-han-sans/releases/download/2.004R/SourceHanSansHWSC.zip
echo -e "download finished\n"

unzip SourceHanSansHWSC.zip

# Copy fonts to font directory
cp -f OTF/SimplifiedChineseHW/SourceHanSansHWSC-Regular.otf /root/.fonts
cp -f OTF/SimplifiedChineseHW/SourceHanSansHWSC-Bold.otf /root/.fonts

sudo fc-cache -fv

