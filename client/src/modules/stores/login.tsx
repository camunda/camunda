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
  ExistingSessionCheck: '/api/authentications/user',
} as const;

class Login {
  isLoggedIn: boolean = false;
  isCheckingExistingSession: boolean = false;

  constructor() {
    /* istanbul ignore if */
    if (document.cookie.includes(CsrfKeyName)) {
      this.checkExistingSession();
    }
  }

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

  /* istanbul ignore next */
  private checkExistingSession = async () => {
    this.isCheckingExistingSession = true;

    if (this.isLoggedIn) {
      this.isCheckingExistingSession = false;

      return;
    }

    const response = await request(Endpoints.ExistingSessionCheck, {
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (response.ok) {
      this.isLoggedIn = true;
    }

    this.isCheckingExistingSession = false;
  };

  reset = () => {
    this.isLoggedIn = false;
    this.isCheckingExistingSession = false;
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
  isCheckingExistingSession: observable,
  handleLogin: action,
  handleLogout: action,
  reset: action,
});

const login = new Login();

export {login};
