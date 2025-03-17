/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {reactQueryClient} from 'common/react-query/reactQueryClient';
import {authenticationStore} from 'common/auth/authentication';

type RequestError =
  | {
      variant: 'network-error';
      response: null;
      networkError: unknown;
    }
  | {
      variant: 'failed-response';
      response: Response;
      networkError: null;
    };

function getCsrfTokenFromStorage() {
  return sessionStorage.getItem('X-CSRF-TOKEN');
}

async function request(
  input: RequestInfo,
  {skipSessionCheck} = {skipSessionCheck: false},
): Promise<
  | {
      response: Response;
      error: null;
    }
  | {
      response: null;
      error: RequestError;
    }
> {
  try {
    const csrfToken = getCsrfTokenFromStorage();
    if (input instanceof Request) {
      const method = input.method;

      if (
        csrfToken &&
        method &&
        ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())
      ) {
        input.headers.append('X-CSRF-TOKEN', csrfToken);
      }
    }

    const response = await fetch(input);

    if (response.ok) {
      authenticationStore.activateSession();
    }

    const tokenFromResponse = response.headers.get('X-CSRF-TOKEN');

    // If the token is found in the response headers, use it
    if (tokenFromResponse) {
      sessionStorage.setItem('X-CSRF-TOKEN', tokenFromResponse);
    }

    if (!skipSessionCheck && response.status === 401) {
      authenticationStore.disableSession();
      reactQueryClient.clear();
    }

    if (response.ok) {
      return {
        response,
        error: null,
      };
    }

    return {
      response: null,
      error: {
        response,
        networkError: null,
        variant: 'failed-response',
      },
    };
  } catch (error) {
    return {
      response: null,
      error: {
        response: null,
        networkError: error,
        variant: 'network-error',
      },
    };
  }
}

function isRequestError(error: unknown): error is {
  variant: 'network-error';
  response: null;
  networkError: Error;
} {
  return (
    error !== null &&
    typeof error === 'object' &&
    'variant' in error &&
    error.variant === 'network-error'
  );
}

export {request, isRequestError};
export type {RequestError};
