/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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

  if ([401, 403].includes(response.status)) {
    authenticationStore.expireSession();
  }

  if (response.ok) {
    authenticationStore.handleThirdPartySessionSuccess();
  }

  return response;
}

export {request};
