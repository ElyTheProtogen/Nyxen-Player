# Nyxen authentication API (Vercel)

Nyxen uses Firebase Authentication as the shared account registry for Android and Windows. The Vercel API verifies Google ID tokens and only issues Firebase custom tokens for existing, email-verified Nyxen accounts.

## Deploy on Vercel (Hobby)

1. Import the `ElyTheProtogen/Nyxen-Player` GitHub repository into Vercel.
2. Select the `feature/firebase-authentication` branch while the PR is still open.
3. Set the Root Directory to the repository root (leave it as `./`).
4. Use the Other framework preset. The API is served from `/api/google-sign-in`.
5. Add the environment variable `FIREBASE_SERVICE_ACCOUNT`. Its value must be the complete Firebase service-account JSON for the `nyxenplayer` project. Create a dedicated service account with the least privilege needed to manage Firebase Authentication users. Store the JSON only in Vercel Environment Variables (Production and Preview as needed). Never commit it or send it in chat.
6. Deploy, then copy the assigned HTTPS deployment domain.

## Important credential setup

Create/download a service-account key from Google Cloud Console only if your organization/account permits it. Treat the private key as a password. Do not paste it into GitHub, source code, or ChatGPT. If Google Cloud requires billing or an organization policy blocks key creation, stop and reassess rather than upgrading or sharing credentials.

## Client endpoint

The deployed endpoint will be:

`https://YOUR-VERCEL-DOMAIN/api/google-sign-in`

After deployment, update the Android and Windows client endpoint constants to this exact URL before testing Google sign-in.

## Behavior

- Verifies the Google ID token signature, issuer, audience, and verified email.
- Looks up only the email proven by that token using Firebase Admin.
- Rejects emails without an existing Nyxen account.
- Requires the existing Firebase account's email to be verified.
- Returns a Firebase custom token only for an eligible account.
- Keeps Firebase Email Enumeration Protection enabled.

Email/password registration and sign-in continue to use Firebase Authentication directly. This API does not expose a general-purpose email-existence lookup.
