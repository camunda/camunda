/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const CSRF_REQUEST_HEADER = "X-CSRF-Token";
const CSRF_RESPONSE_HEADER = "X-CSRF-Token";

const csrfTokenRepository: { token: string | null } = {
  token: null,
};

export function captureCsrfToken(response: Response) {
  const token = response.headers.get(CSRF_RESPONSE_HEADER);
  if (token != null) {
    csrfTokenRepository.token = token;
  }
}

export function getCsrfToken(): string | null {
  return csrfTokenRepository.token;
}

export function getCsrfHeaders(): { [key: string]: string } {
  // It's important to include the CSRF header even when we don't have a token yet as it's used to
  // select the security filter chain for the auth-basic-with-unprotected-api profile.
  return { [CSRF_REQUEST_HEADER]: getCsrfToken() ?? "" };
}
