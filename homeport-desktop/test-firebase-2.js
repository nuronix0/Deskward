const { initializeApp } = require('firebase/app');
const { getDatabase, ref, set, onValue } = require('firebase/database');

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

const ROOM = "2NFYZQ";
console.log(`Setting offer in room ${ROOM}`);

const answerRef = ref(db, `homeport_rooms/${ROOM}/answer`);
onValue(answerRef, (snap) => {
  const ans = snap.val();
  if (ans) {
    console.log("RECEIVED ANSWER FROM DESKTOP:", ans);
    process.exit(0);
  }
});

set(ref(db, `homeport_rooms/${ROOM}/offer`), {
  sdp: "fake-sdp-offer",
  type: "offer"
}).then(() => {
  console.log("Offer set! Waiting 10s to see if desktop responds with answer...");
  setTimeout(() => {
    console.log("TIMED OUT: NO ANSWER RECEIVED");
    process.exit(1);
  }, 10000);
}).catch(e => {
  console.error("Firebase write error:", e);
  process.exit(1);
});
