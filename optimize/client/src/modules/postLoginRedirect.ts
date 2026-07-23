/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * SPIKE (ADR-0038): preserve the current SPA route across a logout/session-expiry -> login cycle.
 *
 * Optimize is a hash-routed SPA, so the active route lives in `window.location.hash` and is never
 * sent to the server (not in the Referer, and dropped when the fetch-based logout navigates to the
 * IdP). We therefore stash it client-side in `sessionStorage` before leaving for the IdP and
 * re-apply it once the app reloads after login. `sessionStorage` survives the cross-origin round
 * trip to the IdP in the same tab, so this is identical for CCSM (Keycloak) and SaaS (Auth0).
 */

const STORAGE_KEY = 'optimizePostLoginRedirect';
const HOME_HASHES = ['', '#', '#/'];

/** Stash the given route (defaults to the current one) unless it is the home or logout route. */
export function storePostLoginRedirect(hash: string = window.location.hash): void {
  if (!HOME_HASHES.includes(hash) && hash !== '#/logout') {
    sessionStorage.setItem(STORAGE_KEY, hash);
  }
}

/**
 * Re-apply a stashed route after login. Only overrides the location when the app landed on home; if
 * the browser already carried the original route through the login redirect chain, it is left as
 * is. Always clears the stash so it applies at most once.
 */
export function restorePostLoginRedirect(): void {
  const stored = sessionStorage.getItem(STORAGE_KEY);
  if (stored === null) {
    return;
  }
  sessionStorage.removeItem(STORAGE_KEY);
  if (HOME_HASHES.includes(window.location.hash)) {
    window.location.hash = stored;
  }
}
