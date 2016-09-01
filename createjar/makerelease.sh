rm release.zip
rm update.zip
cd release
zip -r release.zip * -x "*.DS_Store"
cp release.zip ../
zip update.zip PPkTool.jar PPkDaemon.jar
cp update.zip ../
cd ../
