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

import {logger} from 'modules/logger';
import {authenticationStore} from 'modules/stores/authentication';
import {mergePathname} from './mergePathname';

type RequestParams = {
  url: string;
  method?: RequestInit['method'];
  headers?: RequestInit['headers'];
  body?: string | unknown;
  signal?: RequestInit['signal'];
};

async function request({url, method, body, headers, signal}: RequestParams) {
  const csrfToken = sessionStorage.getItem('X-CSRF-TOKEN');
  const hasCsrfToken =
    csrfToken !== null &&
    method !== undefined &&
    ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);

  const response = await fetch(
    mergePathname(window.clientConfig?.contextPath ?? '/', url),
    {
      method,
      credentials: 'include',
      body: typeof body === 'string' ? body : JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        ...(hasCsrfToken ? {'X-CSRF-TOKEN': csrfToken} : {}),
        ...headers,
      },
      mode: 'cors',
      signal,
    },
  );

  if (response.status === 401) {
    authenticationStore.expireSession();
  }

  if (response.ok) {
    authenticationStore.handleThirdPartySessionSuccess();

    const csrfToken = response.headers.get('X-CSRF-TOKEN');
    if (csrfToken !== null) {
      sessionStorage.setItem('X-CSRF-TOKEN', csrfToken);
    }
  }

  return response;
}

async function requestAndParse<T>(
  params: RequestParams,
  options?: {
    onFailure?: () => void;
    onException?: () => void;
    isPolling?: boolean;
  },
) {
  const {url} = params;

  const extendedParams = {
    ...params,
    headers: {...params.headers, 'x-is-polling': 'true'},
  };

  try {
    const response = await request(
      options?.isPolling ? extendedParams : params,
    );

    if (!response.ok) {
      options?.onFailure?.();

      logger.error(`Failed to fetch ${url}`);

      return {
        isSuccess: false,
        statusCode: response.status,
        data: undefined,
      } as const;
    }
    return {
      isAborted: false,
      isSuccess: true,
      statusCode: response.status,
      data: (response.headers.get('content-type')?.includes('application/json')
        ? await response.json()
        : await response.text()) as T,
    } as const;
  } catch (error) {
    options?.onException?.();

    if (error instanceof DOMException && error.name === 'AbortError') {
      return {
        isAborted: true,
        isSuccess: false,
        statusCode: 0,
        data: undefined,
      } as const;
    }

    logger.error(`Failed to fetch ${url}`);
    logger.error(error);
    return {
      isAborted: false,
      isSuccess: false,
      statusCode: 0,
      data: undefined,
    } as const;
  }
}

export {request, requestAndParse};
