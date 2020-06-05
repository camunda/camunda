/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {decorate, observable, action} from 'mobx';

import {getCsrfToken, CsrfKeyName} from 'modules/utils/getCsrfToken';

const Endpoints = {
  Login: '/api/login',
  Logout: '/api/logout',
} as const;

class Login {
  isLoggedIn: boolean = true;

  handleLogin = async (username: string, password: string) => {
    const response = await request(Endpoints.Login, {
      method: 'POST',
      body: new URLSearchParams({username, password}),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    if (!response.ok) {
      throw new Error('Login failed');
    }

    this.isLoggedIn = true;
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

    this.isLoggedIn = false;
  };

  disableSession = () => {
    this.isLoggedIn = false;
  };

  reset = () => {
    this.isLoggedIn = true;
  };
}

/* istanbul ignore next */
function request(input: string, init?: RequestInit) {
  const token = getCsrfToken(document.cookie);

  return fetch(input, {
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

decorate(Login, {
  isLoggedIn: observable,
  disableSession: action,
  handleLogin: action,
  handleLogout: action,
  reset: action,
});

const login = new Login();

export {login};
