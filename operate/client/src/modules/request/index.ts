/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

async function requestWithThrow({
  url,
  method,
  body,
  headers,
  signal,
}: RequestParams) {
  const response = await request({url, method, body, headers, signal});

  if (!response.ok) {
    const json = await response.json();
    throw new Error(json.error);
  }

  return response;
}

async function request({url, method, body, headers, signal}: RequestParams) {
  const csrfToken = sessionStorage.getItem('X-CSRF-TOKEN');
  const hasCsrfToken =
    csrfToken !== null &&
    method !== undefined &&
    ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase());

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

export {request, requestAndParse, requestWithThrow};
