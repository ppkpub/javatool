mkdir build
cd createjar
sh packager.sh
sh makerelease.sh
cd ../
cp createjar/release.zip build/PPk-newest.zip
cp createjar/update.zip build/update.zip
unzip -u -o -d build/ build/update.zip
