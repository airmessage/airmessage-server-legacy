#This script installs a new version of AirMessage to the Applications directory

#Wait for AirMessage to exit
sleep 2

#Delete old AirMessage installation
rm -rf /Applications/AirMessage.app

#Move the new app to the Applications directory
mv "$1" /Applications

#Clean the update directory
rm -r "$(dirname "$1")"

#Wait for app to be registered
sleep 1

#Open the new app
open /Applications/AirMessage.app

for i in 1 2 3 4 5
do
	open /Applications/AirMessage.app && break || sleep 1
done