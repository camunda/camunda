/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
      const tokenFromResponse = response.headers.get('X-CSRF-TOKEN');

      // If the token is found in the response headers, use it
      if (tokenFromResponse) {
        sessionStorage.setItem('X-CSRF-TOKEN', tokenFromResponse);
      }
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
    typeof error === 'object' &&
    error !== null &&
    'variant' in error &&
    error.variant === 'network-error'
  );
}

export {request, isRequestError};
export type {RequestError};
