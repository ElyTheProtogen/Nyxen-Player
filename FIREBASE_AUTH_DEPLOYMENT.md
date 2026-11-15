# Firebase authentication deployment

Nyxen uses the Firebase project `nyxenplayer` for shared Android and Windows accounts.

## One-time Firebase setup

1. Open the [Firebase usage and billing page](https://console.firebase.google.com/project/nyxenplayer/usage).
2. Cloud Functions deployment requires the **Blaze (pay-as-you-go)** plan and a linked Cloud Billing account. Review the pricing and set a budget alert before upgrading.
3. Keep **Email Enumeration Protection enabled**.
4. Ensure Google is enabled as a sign-in provider in Firebase Authentication.

## Deploy the trusted Google account check

From the repository root, with Node.js 22 and the Firebase CLI installed:

```sh
npm install -g firebase-tools
firebase login
npm --prefix functions install
firebase deploy --only functions:googleSignInForExistingUser
```

The function is deployed in `us-central1` at:

`https://us-central1-nyxenplayer.cloudfunctions.net/googleSignInForExistingUser`

Both clients call this endpoint with a Google ID token. The function verifies the token's signature, issuer, audience, and verified email, then looks up that same email using Firebase Admin. It returns a Firebase custom token only when a verified Nyxen account already exists. It does not provide a general-purpose email lookup endpoint.

## Cost and security notes

- Cloud Functions requires Blaze even though the product has a monthly no-cost usage tier. A billing account is still required; usage beyond free quotas and deployment-related storage can incur charges.
- Configure a budget alert in Google Cloud Billing. A budget alert is not itself a hard spending cap.
- Never commit service-account JSON files, private keys, or Admin SDK credentials. The deployed function uses its attached service identity and Firebase Admin SDK.
- Do not disable Email Enumeration Protection to make client-side account lookup work.
- Deploy the function before testing Google sign-in in either app. Email/password registration and verification continue to use Firebase Authentication directly.
