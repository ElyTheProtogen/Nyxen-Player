'use strict';

const { initializeApp, getApps, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { OAuth2Client } = require('google-auth-library');

const GOOGLE_WEB_CLIENT_ID = '936657455185-28l7q0jf6nkh4m44fv5jjmah6qi0a3gk.apps.googleusercontent.com';
const GOOGLE_WINDOWS_DESKTOP_CLIENT_ID = '936657455185-59pnkfdb20lfgf9moujtg86gb7o62ghf.apps.googleusercontent.com';
const GOOGLE_ALLOWED_CLIENT_IDS = [GOOGLE_WEB_CLIENT_ID, GOOGLE_WINDOWS_DESKTOP_CLIENT_ID];
const googleClient = new OAuth2Client();

function adminAuth() {
  if (!getApps().length) {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!raw) throw new Error('FIREBASE_SERVICE_ACCOUNT is not configured');
    initializeApp({ credential: cert(JSON.parse(raw)) });
  }
  return getAuth();
}

module.exports = async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') return res.status(204).end();
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST, OPTIONS');
    return res.status(405).json({ error: 'Method not allowed.' });
  }

  try {
    const idToken = typeof req.body?.idToken === 'string' ? req.body.idToken : '';
    if (!idToken || idToken.length > 12000) {
      return res.status(400).json({ error: 'A valid Google sign-in token is required.' });
    }

    const ticket = await googleClient.verifyIdToken({
      idToken,
      audience: GOOGLE_ALLOWED_CLIENT_IDS
    });
    const google = ticket.getPayload();
    if (!google?.sub || !google.email || google.email_verified !== true) {
      return res.status(401).json({ error: 'Google did not provide a verified email address.' });
    }

    const auth = adminAuth();
    let user;
    try {
      user = await auth.getUserByEmail(google.email);
    } catch (error) {
      if (error?.code === 'auth/user-not-found') {
        return res.status(403).json({
          error: "This email isn't registered with Nyxen. Please create an account first."
        });
      }
      throw error;
    }

    if (!user.emailVerified) {
      return res.status(403).json({
        error: 'Please verify your Nyxen email address before signing in.'
      });
    }

    const customToken = await auth.createCustomToken(user.uid);
    return res.status(200).json({
      customToken,
      profile: {
        uid: user.uid,
        email: user.email,
        displayName: user.displayName || google.name || user.email,
        photoURL: user.photoURL || google.picture || ''
      }
    });
  } catch (error) {
    console.error('Nyxen Google sign-in failed:', error?.message || error);
    return res.status(401).json({ error: 'Google sign-in could not be verified. Please try again.' });
  }
};
