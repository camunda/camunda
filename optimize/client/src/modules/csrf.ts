/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// CSRF protection for the CSL security chains. Self-gating: with no server-issued token
// (flag off / legacy edition) nothing is sent, so it stays a no-op there.
// See ADR-0038: https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md
const CSRF_TOKEN_HEADER = 'X-CSRF-TOKEN';
const CSRF_PROTECTED_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];

export function csrfRequestHeader(method: string | undefined): Record<string, string> {
  const token = sessionStorage.getItem(CSRF_TOKEN_HEADER);
  if (token && method && CSRF_PROTECTED_METHODS.includes(method.toUpperCase())) {
    return {[CSRF_TOKEN_HEADER]: token};
  }
  return {};
}

export function storeCsrfToken(response: Response): void {
  // Only capture the token from a successful response (like Operate/Tasklist): a token issued on a
  // 401 by the pre-login chain must not overwrite a valid one and get echoed on the next write.
  if (!response.ok) {
    return;
  }
  // Guard responses whose headers are missing or don't implement `get` (test doubles) so token
  // capture never throws.
  const headers = response.headers as Headers | undefined;
  const token = headers?.get?.(CSRF_TOKEN_HEADER);
  if (token) {
    sessionStorage.setItem(CSRF_TOKEN_HEADER, token);
  }
}
