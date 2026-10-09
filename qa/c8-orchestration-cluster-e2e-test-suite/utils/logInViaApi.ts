/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext, APIResponse} from '@playwright/test';

/**
 * Logs in over the API and sends back the CSRF token of the new session. The login endpoint always
 * enforces CSRF, and a GET of the login endpoint is the only place where the server sends a token to an
 * anonymous caller, so the token must be fetched before the POST. The request context keeps the
 * cookie of the GET, which the server compares the token against.
 *
 * The token comes from the login response if the server rotates it, and from the login page if it
 * does not.
 */
export async function logInViaApi(
  request: APIRequestContext,
  baseUrl: string,
  credentials: {username: string; password: string},
): Promise<{response: APIResponse; csrfToken: string}> {
  const loginUrl = `${baseUrl}/login`;
  const loginPage = await request.get(loginUrl, {
    headers: {Accept: 'text/html'},
  });
  const csrfTokenOfLoginPage = loginPage.headers()['x-csrf-token'] ?? '';

  const response = await request.post(loginUrl, {
    form: credentials,
    headers: csrfTokenOfLoginPage ? {'X-CSRF-TOKEN': csrfTokenOfLoginPage} : {},
  });

  return {
    response,
    csrfToken: response.headers()['x-csrf-token'] ?? csrfTokenOfLoginPage,
  };
}
