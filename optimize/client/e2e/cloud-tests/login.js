/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ClientFunction, Selector} from 'testcafe';

import config from '../config';

const usernameInput = Selector('input[name="username"]');
const passwordInput = Selector('input[name="password"]');

const FORM_TIMEOUT = 30000;
const MAX_ATTEMPTS = 3;
const RETRY_DELAY = 10000;

// Fill one field of the Auth0 Universal Login form and submit it through the browser's own submit
// path rather than a synthetic button click.
//
// Root cause this avoids: under TestCafe native automation the synthetic submit is intermittently
// not delivered to Chrome in CI, so the credentials are typed but the form never POSTs and the
// browser is stranded on the login page (surfacing as "selector did not match" on the app shell).
// The same race was root-caused and fixed for the self-managed Keycloak login in e2e/utils.js.
// `requestSubmit` is given the submit button as its submitter so Auth0's `name="action"` value is
// part of the payload, which a bare `form.submit()` would drop.
const fillAndSubmit = ClientFunction((fieldName, value) => {
  const input = document.querySelector(`input[name="${fieldName}"]`);
  if (!input) {
    return `missing-field:${fieldName}`;
  }
  const form = input.form;
  if (!form) {
    return 'no-form';
  }

  const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setValue.call(input, value);
  input.dispatchEvent(new Event('input', {bubbles: true}));
  input.dispatchEvent(new Event('change', {bubbles: true}));

  const submitter = form.querySelector('button[type="submit"]');
  if (!submitter) {
    // Submitting without it would drop `name="action"` and Auth0 would reject the request, so
    // report the missing button rather than sending a payload we know is incomplete.
    return 'no-submit-button';
  }

  if (form.requestSubmit) {
    form.requestSubmit(submitter);
  } else {
    form.submit();
  }
  return 'ok';
});

// Auth0 renders rejections into the page. Reading it turns an opaque selector timeout into an
// actionable message ("Wrong email or password", "your account has been blocked", ...).
//
// A message found here is treated as final, so it must not be something Optimize itself rendered:
// mistaking an app toast for a rejection would suppress the retry for a merely transient failure.
// Hence the Auth0-specific selectors first, and the generic `[role="alert"]` only while the browser
// is still on Auth0's own origin.
const readAuth0Error = ClientFunction((appOrigin) => {
  const auth0Element = document.querySelector(
    '.ulp-input-error-message, #error-element-username, #error-element-password'
  );
  if (auth0Element) {
    return auth0Element.textContent.trim();
  }

  if (window.location.origin === appOrigin) {
    return '';
  }

  const alert = document.querySelector('[role="alert"]');
  return alert ? alert.textContent.trim() : '';
});

/**
 * Authenticates against the Auth0 dev tenant and leaves the browser in the logged-in Optimize app.
 *
 * Retries only failures where Auth0 never rejected us (page never rendered, redirect stalled). An
 * explicit Auth0 error is final: the credentials or the account are the problem, and retrying only
 * pushes further into Auth0's per-IP brute-force throttling.
 */
export async function loginToCloud(t) {
  const email = requireEnv('AUTH0_USEREMAIL');
  const password = requireEnv('AUTH0_USERPASSWORD');

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      await attemptLogin(t, email, password);
      return;
    } catch (error) {
      if (error.auth0Rejected || attempt === MAX_ATTEMPTS) {
        throw error;
      }
      await t.wait(RETRY_DELAY);
    }
  }

  throw new Error('unreachable: the last attempt either returns or rethrows');
}

async function attemptLogin(t, email, password) {
  // Start from a clean cookie jar so a lingering Auth0 SSO session cannot silently authenticate us
  // as a different identity than the one under test.
  await t.deleteCookies();
  await t.navigateTo(config.endpoint);

  await submitField(usernameInput, 'username', email);
  await submitField(passwordInput, 'password', password);

  // The password POST redirects back through Optimize's /sso-callback; we are authenticated once
  // the login form is gone.
  try {
    await t.expect(usernameInput.exists).notOk({timeout: FORM_TIMEOUT});
  } catch {
    throw await loginError('login did not complete');
  }
}

async function submitField(selector, fieldName, value) {
  // Optimize redirects to Auth0, so the field is briefly absent right after navigation.
  if (!(await selector.with({timeout: FORM_TIMEOUT}).visible)) {
    throw await loginError(`the "${fieldName}" field never appeared`);
  }

  const result = await fillAndSubmit(fieldName, value);
  if (result !== 'ok') {
    throw await loginError(`could not submit the "${fieldName}" field (${result})`);
  }
}

async function loginError(reason) {
  const auth0Message = await readAuth0Error(new URL(config.endpoint).origin);
  const error = new Error(
    `Auth0 login failed: ${reason}${auth0Message ? ` - Auth0 said: "${auth0Message}"` : ''}`
  );
  error.auth0Rejected = Boolean(auth0Message);
  return error;
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable ${name} for the cloud smoke test`);
  }
  return value;
}
