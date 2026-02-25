/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {logger} from 'modules/logger';
import {authenticationStore} from 'modules/stores/authentication';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {mergePathname} from './mergePathname';

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

type RequestResult<T> = Promise<
  | {
      response: T;
      error: null;
    }
  | {
      response: null;
      error: RequestError;
    }
>;

type RequestParams = {
  url: string;
  method?: RequestInit['method'];
  headers?: RequestInit['headers'];
  body?: string | unknown;
  signal?: RequestInit['signal'];
  responseType?: 'json' | 'text' | 'none';
};

async function requestWithThrow<T>({
  url,
  method,
  body,
  headers,
  signal,
  responseType = 'json',
}: RequestParams): RequestResult<T> {
  try {
    const response = await request({url, method, body, headers, signal});

    if (response.ok) {
      if (responseType === 'none') {
        return {response: null as T, error: null};
      }

      if (responseType === 'text') {
        return {response: (await response.text()) as T, error: null};
      }

      if (responseType === 'json') {
        return {response: (await response.json()) as T, error: null};
      }
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

async function request(
  {url, method, body, headers, signal}: RequestParams,
  {skipSessionCheck = false}: {skipSessionCheck?: boolean} = {},
) {
  const clientConfig = getClientConfig();
  const csrfToken = sessionStorage.getItem('X-CSRF-TOKEN');
  const hasCsrfToken =
    csrfToken !== null &&
    method !== undefined &&
    ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase());

  const response = await fetch(mergePathname(clientConfig.contextPath, url), {
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
  });

  if (!skipSessionCheck && response.status === 401) {
    authenticationStore.disableSession();
  }

  if (response.ok) {
    authenticationStore.activateSession();

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

function isRequestError(error: unknown): error is RequestError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'variant' in error &&
    (error.variant === 'network-error' || error.variant === 'failed-response')
  );
}

export {request, requestAndParse, requestWithThrow, isRequestError};
export type {RequestError};
