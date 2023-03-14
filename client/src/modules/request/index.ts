/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  const response = await fetch(
    mergePathname(window.clientConfig?.contextPath ?? '/', url),
    {
      method,
      credentials: 'include',
      body: typeof body === 'string' ? body : JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      mode: 'cors',
      signal,
    }
  );

  if (response.status === 401) {
    authenticationStore.expireSession();
  }

  if (response.ok) {
    authenticationStore.handleThirdPartySessionSuccess();
  }

  return response;
}

async function requestAndParse<T>(
  params: RequestParams,
  options?: {onFailure?: () => void; onException?: () => void}
) {
  const {url} = params;

  try {
    const response = await request(params);

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
