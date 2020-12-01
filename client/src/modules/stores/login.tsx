/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable} from 'mobx';

import {getCsrfToken, CsrfKeyName} from 'modules/utils/getCsrfToken';
import {resetApolloStore} from 'modules/apollo-client';
import {mergePathname} from 'modules/utils/mergePathname';

const BASENAME = window.clientConfig?.contextPath ?? '/';

const Endpoints = {
  Login: '/api/login',
  Logout: '/api/logout',
} as const;

class Login {
  status:
    | 'initial'
    | 'logged-in'
    | 'logged-out'
    | 'session-expired'
    | 'session-invalid' = 'initial';

  constructor() {
    makeAutoObservable(this);
  }

  handleLogin = async (username: string, password: string) => {
    const response = await request(Endpoints.Login, {
      method: 'POST',
      body: new URLSearchParams({username, password}),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    if (response.ok) {
      this.activateSession();
    }

    return response;
  };

  logout = () => {
    this.status = 'logged-out';
  };

  activateSession = () => {
    this.status = 'logged-in';
  };

  handleLogout = async () => {
    const response = await request(Endpoints.Logout, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error('Logout failed');
    }

    resetApolloStore();

    this.logout();
  };

  disableSession = () => {
    if (['session-invalid', 'session-expired'].includes(this.status)) {
      return;
    }

    if (this.status === 'initial') {
      this.status = 'session-invalid';
    } else {
      this.status = 'session-expired';
    }
  };

  reset = () => {
    this.status = 'initial';
  };
}

/* istanbul ignore next */
function request(input: string, init?: RequestInit) {
  const token = getCsrfToken(document.cookie);

  return fetch(mergePathname(BASENAME, input), {
    ...init,
    credentials: 'include',
    mode: 'cors',
    headers:
      token === null
        ? init?.headers
        : {
            ...init?.headers,
            [CsrfKeyName]: token,
          },
  });
}

const login = new Login();

export {login};
