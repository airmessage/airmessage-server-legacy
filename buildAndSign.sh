JDEPS="java.base,java.desktop,java.logging,java.sql,java.xml,java.datatransfer,java.compiler,jdk.unsupported,java.naming,jdk.crypto.ec,jdk.httpserver"
OUTPUT_DIR="build/app"

JAVA_HOME=$(/usr/libexec/java_home -v 15)
VERSION=$(./gradlew -q printVersionName)

SIGNATURE=$1 # Apple signing ID: "Developer ID Application: Developer Name (DUCNFCN445)"
NOTARIZATION_USERNAME=$2 # Apple ID username: "developer@example.com"
NOTARIZATION_PASSKEY=$3 # Apple ID password Keychain listing: "AC_PASSWORD"

echo "Preparing AirMessage Server v$VERSION"

#Clean up old files
./gradlew clean

#Assemble app files
./gradlew build
./gradlew copyToLib

#Prepare tmp directory
mkdir build/libs/tmp
pushd build/libs/tmp

#Sign native JAR libraries
for f in ../*.jar;
do
	echo "Re-signing $(basename "$f")"

	jar xf "$f" #Unpack
	rm "$f" #Delete original JAR
	find -E . -regex ".*\.(dylib|jnilib)" -print0 | xargs codesign --force --verbose --sign "$SIGNATURE" #Codesign dynamic libraries
	jar cmf META-INF/MANIFEST.MF "$f" ./* #Repack JAR
	rm -r ./* #Empty directory
done

#Clean up tmp directory
popd
rm -rf build/libs/tmp

#Create app directory
mkdir $OUTPUT_DIR

APP_FILE="$OUTPUT_DIR/AirMessage.app"
PACKAGE_FILE="$OUTPUT_DIR/server-v$VERSION.zip"

#Package app
echo "Packaging app"
$JAVA_HOME/bin/jpackage \
	--name "AirMessage" \
	--app-version "$VERSION" \
	--input "build/libs" \
	--main-jar "$(./gradlew -q printJarName)" \
	--main-class "me.tagavari.airmessageserver.server.Main" \
	--type "app-image" \
	--java-options "-XstartOnFirstThread" \
	--add-modules "$JDEPS" \
	--mac-package-identifier "me.tagavari.airmessageserver" \
	--mac-package-name "AirMessage" \
	--mac-package-signing-prefix "airmessage" \
	--icon "AirMessage.icns" \
	--dest $OUTPUT_DIR

#Update app plist
echo "Fixing plist"
plutil -insert LSUIElement -string True "$APP_FILE/Contents/Info.plist" #Hide dock icon
plutil -insert NSAppTransportSecurity -xml "<dict><key>NSAllowsLocalNetworking</key><true/></dict>" "$APP_FILE/Contents/Info.plist" #Enable local networking (for AirMessage Connect sign-in)

#Sign app
echo "Signing app"
codesign --force --options runtime --entitlements "macos.entitlements" --sign "$SIGNATURE" "$APP_FILE/Contents/runtime/Contents/MacOS/libjli.dylib"
codesign --force --options runtime --entitlements "macos.entitlements" --sign "$SIGNATURE" "$APP_FILE/Contents/MacOS/AirMessage"
codesign --force --options runtime --entitlements "macos.entitlements" --sign "$SIGNATURE" "$APP_FILE"

#Package app to ZIP
echo "Compressing app for notarization"
ditto -c -k --keepParent "$APP_FILE" "$PACKAGE_FILE"

#Notarize app
echo "Uploading app to Apple notarization service"
REQUEST_UUID=$(xcrun altool --notarize-app \
	--primary-bundle-id "me.tagavari.airmessageserver" \
	--username $NOTARIZATION_USERNAME \
	--password "@keychain:$NOTARIZATION_PASSKEY" \
	--file "$PACKAGE_FILE" \
	| grep RequestUUID | awk '{print $3}')
rm "$PACKAGE_FILE"

#Wait for notarization to finish
echo "Waiting for notarization ID $REQUEST_UUID to finish"
while true; do
	NOTARIZATION_STATUS=$(xcrun altool --notarization-info "$REQUEST_UUID" --username "$NOTARIZATION_USERNAME" --password "@keychain:$NOTARIZATION_PASSKEY")
	if echo "$NOTARIZATION_STATUS" | grep -q "Status: in progress"; then sleep 20
	elif echo "$NOTARIZATION_STATUS" | grep -q "Status: success"; then break
	else
		>&2 echo "$NOTARIZATION_STATUS"
		exit
	fi
done

#Staple ticket
echo "Stapling ticket"
xcrun stapler staple "$APP_FILE"

#Check for signatures
echo "Verifying files"
spctl --assess "$APP_FILE"
codesign --verify "$APP_FILE"

#Re-compress app
echo "Compressing final app to $PACKAGE_FILE"
ditto -c -k --keepParent "$APP_FILE" "$PACKAGE_FILE"

echo "Successfully built AirMessage Server v$VERSION for distribution"