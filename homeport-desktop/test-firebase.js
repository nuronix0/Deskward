const { initializeApp } = require('firebase/app');
const { getDatabase, ref, set } = require('firebase/database');

const FIREBASE_CONFIG = {
  apiKey:            "AIzaSyBKdp85ClFsBWtaaKY1D4g7Hr7Y_a0eJok",
  authDomain:        "deskward10.firebaseapp.com",
  databaseURL:       "https://deskward10-default-rtdb.firebaseio.com",
  projectId:         "deskward10",
  storageBucket:     "deskward10.firebasestorage.app",
  messagingSenderId: "448678937570",
  appId:             "1:448678937570:web:302ef022a1e9ae9e8defbb"
};

const app = initializeApp(FIREBASE_CONFIG);
const db = getDatabase(app);

const ROOM = "N9UUEL";
console.log(`Setting offer in room ${ROOM}`);

set(ref(db, `homeport_rooms/${ROOM}/offer`), {
  sdp: "fake-sdp-offer",
  type: "offer"
}).then(() => {
  console.log("Offer set! Waiting 5s to see if desktop responds...");
  setTimeout(() => process.exit(0), 5000);
}).catch(e => {
  console.error("Firebase write error:", e);
  process.exit(1);
});
