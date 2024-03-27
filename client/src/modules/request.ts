/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {reactQueryClient} from './react-query/reactQueryClient';
import {authenticationStore} from './stores/authentication';

type RequestError = {
  variant: 'network-error' | 'failed-response';
  response: Response | null;
  networkError: unknown | null;
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

    if (!skipSessionCheck && response.status === 401) {
      authenticationStore.disableSession();
      reactQueryClient.clear();
    }

    if (response.ok) {
      const tokenFromResponse = response.headers.get('X-CSRF-TOKEN');

      // If the token is found in the response headers, use it
      if (tokenFromResponse) {
        sessionStorage.setItem('X-CSRF-TOKEN', tokenFromResponse);
      }
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
    typeof error === 'object' &&
    error !== null &&
    'variant' in error &&
    error.variant === 'network-error'
  );
}

export {request, isRequestError};
export type {RequestError};
