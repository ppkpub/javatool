PPk open management tool

A simple&open tool for registing , managing and browsing ODIN ,which is a decentralized name  
based on Bitocin blockchain.

==================================================================
Install

1.You need install JAVA JRE (1.8.0 or above) first.

  Download free Java software from here: http://www.java.com/getjava/

2.Unzip "PPk-x.x.zip" then run the ppk.bat (Windows) or ppk.sh (Linux) .

Or you can run the command like below:

   java -Dfile.encoding=UTF-8 -jar PPkTool.jar GUI 

==================================================================
Settings

You can find the configure file named "ppk.conf" in the "resources" folder.
   
1.The standard transaction fee for Bitcoin is 10000 Satoshi witch equals to 0.0001 BTC. 
If you want to change it, you can modify the value of "StandardFeeSatoshi".

2.The default language setting is "Lang=EN" which means english. 
If you want to use chinese, you can modify the value to "Lang=CN".

==================================================================
Upgrade

If you had installed the older version,please upgrade to the newest version.
Before you upgrade ,you must backup your wallet file. The wallet file (wallet.dat and files such as "ck-*") can be found in the "resources/db" folder.Then unzip the downloaded zip file (PPk-x.x.zip) to override the older files.

==================================================================
For more information, please see http://ppkpub.org/
