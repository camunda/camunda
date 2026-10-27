/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Preserve the current SPA route across the session-expiry -> login cycle.
 *
 * Optimize is a hash-routed SPA, so the active route lives in `window.location.hash` and is never
 * sent to the server. When a request returns 401, `PrivateRoute` reloads and the CSL chain sends the
 * browser through a cross-origin IdP round trip that drops the hash. We therefore stash the route
 * client-side in `sessionStorage` before that reload and re-apply it once the app lands back on home
 * after login. `sessionStorage` survives the cross-origin round trip in the same tab, so this is
 * identical for CCSM (Keycloak) and SaaS (Auth0). Explicit logout does not stash: only the 401
 * handler in `PrivateRoute` writes here.
 *
 * See ADR-0038:
 * https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md
 */

const STORAGE_KEY = 'optimizePostLoginRedirect';
const HOME_HASHES = ['', '#', '#/'];

/** Stash the given route (defaults to the current one) unless it is the home route. */
export function storePostLoginRedirect(hash: string = window.location.hash): void {
  if (!HOME_HASHES.includes(hash)) {
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
