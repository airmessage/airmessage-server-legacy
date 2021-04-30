import firebase from "firebase/app";
import "firebase/auth";
import * as firebaseui from "firebaseui";
import {firebaseConfig} from "./secrets";

// Initialize Firebase
firebase.initializeApp(firebaseConfig);

// Initialize the FirebaseUI Widget using Firebase.
const ui = new firebaseui.auth.AuthUI(firebase.auth());

// The start method will wait until the DOM is loaded.
ui.start('#firebaseui-auth-container', {
	callbacks: {
		signInSuccessWithAuthResult: (authResult) => {
			document.getElementById("desc").innerHTML = "Please wait&#8230;"
			const userID = authResult.user.uid;
			
			// Generate an ID token
			firebase.auth().currentUser.getIdToken(true).then((idToken) => {
				// Send back to Java app
				accountFunctionCallback(idToken, userID);
			}).catch((error) => {
				// Handle error
				accountFunctionErrorCallback(error.name, error.message);
			});
			
			// Disable automatic redirect
			return false;
		}
	},
	signInOptions: [
		// Leave the lines as is for the providers you want to offer your users.
		{
			provider: firebase.auth.GoogleAuthProvider.PROVIDER_ID,
			customParameters: {
				// Forces account selection even when one account is available.
				prompt: "select_account"
			}
		}
		// firebase.auth.EmailAuthProvider.PROVIDER_ID
	]
});