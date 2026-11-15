'use strict';

const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { onRequest } = require('firebase-functions/v2/https');
const { OAuth2Client } = require('google-auth-library');

initializeApp();

const GOOGLE_WEB_CLIENT_ID = '936657455185-28l7q0jf6nkh4m44fv5jjmah6qi0a3gk.apps.googleusercontent.com';
const googleClient = new OAuth2Client(GOOGLE_WEB_CLIENT_ID);

exports.googleSignInForExistingUser = onRequest(
  { region: 'us-central1', cors: true, maxInstances: 10 },
  async (req, res) => {
    res.set('Cache-Control', 'no-store');

    if (req.method !== 'POST') {
      res.set('Allow', 'POST').status(405).json({ error: 'Method not allowed.' });
      return;
    }

    try {
      const idToken = typeof req.body?.idToken === 'string' ? req.body.idToken : '';
      if (!idToken || idToken.length > 12000) {
        res.status(400).json({ error: 'A valid Google sign-in token is required.' });
        return;
      }

      // Verify the Google identity and its audience before looking up any Nyxen account.
      const ticket = await googleClient.verifyIdToken({
        idToken,
        audience: GOOGLE_WEB_CLIENT_ID
      });
      const google = ticket.getPayload();
      if (!google?.sub || !google.email || google.email_verified !== true) {
        res.status(401).json({ error: 'Google did not provide a verified email address.' });
        return;
      }

      // The caller can only query the account for the Google identity they proved.
      // This avoids exposing a general-purpose email-existence lookup endpoint.
      let user;
      try {
        user = await getAuth().getUserByEmail(google.email);
      } catch (error) {
        if (error?.code === 'auth/user-not-found') {
          res.status(403).json({
            error: "This email isn't registered with Nyxen. Please create an account first."
          });
          return;
        }
        throw error;
      }

      if (!user.emailVerified) {
        res.status(403).json({
          error: 'Please verify your Nyxen email address before signing in.'
        });
        return;
      }

      const customToken = await getAuth().createCustomToken(user.uid);
      res.status(200).json({
        customToken,
        profile: {
          uid: user.uid,
          email: user.email,
          displayName: user.displayName || google.name || user.email,
          photoURL: user.photoURL || google.picture || ''
        }
      });
    } catch (error) {
      // Never return Admin SDK details or raw token-validation errors to clients.
      console.error('Nyxen Google sign-in failed:', error?.message || error);
      res.status(401).json({ error: 'Google sign-in could not be verified. Please try again.' });
    }
  }
);
