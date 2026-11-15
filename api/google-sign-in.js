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
    let idToken = typeof req.body?.idToken === 'string' ? req.body.idToken : '';

    // Windows desktop clients exchange authorization codes here so the OAuth
    // client secret stays on Vercel rather than inside the distributed app.
    if (!idToken && typeof req.body?.authorizationCode === 'string') {
      const authorizationCode = req.body.authorizationCode;
      const codeVerifier = typeof req.body?.codeVerifier === 'string' ? req.body.codeVerifier : '';
      const redirectUri = typeof req.body?.redirectUri === 'string' ? req.body.redirectUri : '';
      const clientSecret = process.env.GOOGLE_WINDOWS_CLIENT_SECRET;

      let parsedRedirect;
      try { parsedRedirect = new URL(redirectUri); } catch {}
      if (!authorizationCode || authorizationCode.length > 4096 ||
          !/^[A-Za-z0-9._~-]{43,128}$/.test(codeVerifier) ||
          !parsedRedirect || parsedRedirect.protocol !== 'http:' ||
          parsedRedirect.hostname !== '127.0.0.1' ||
          parsedRedirect.pathname !== '/oauth2callback' ||
          !parsedRedirect.port || parsedRedirect.search || parsedRedirect.hash) {
        return res.status(400).json({ error: 'Invalid desktop Google sign-in request.' });
      }
      if (!clientSecret) {
        console.error('GOOGLE_WINDOWS_CLIENT_SECRET is not configured');
        return res.status(503).json({ error: 'Google sign-in is not configured on the server yet.' });
      }

      const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          client_id: GOOGLE_WINDOWS_DESKTOP_CLIENT_ID,
          client_secret: clientSecret,
          code: authorizationCode,
          code_verifier: codeVerifier,
          grant_type: 'authorization_code',
          redirect_uri: redirectUri
        })
      });
      const tokens = await tokenResponse.json().catch(() => ({}));
      if (!tokenResponse.ok || typeof tokens.id_token !== 'string') {
        console.error('Google desktop token exchange failed:', tokens.error || 'unknown_error', tokens.error_description || '');
        return res.status(401).json({ error: 'Google could not complete desktop sign-in. Please try again.' });
      }
      idToken = tokens.id_token;
    }

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
