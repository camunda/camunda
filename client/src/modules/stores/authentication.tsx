/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action} from 'mobx';

import {resetApolloStore} from 'modules/apollo-client';
import {mergePathname} from 'modules/utils/mergePathname';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

const BASENAME = window.clientConfig?.contextPath ?? '/';

const Endpoints = {
  Login: '/api/login',
  Logout: '/api/logout',
} as const;

type Status =
  | 'initial'
  | 'logged-in'
  | 'logged-out'
  | 'session-expired'
  | 'session-invalid'
  | 'invalid-third-party-session';

const DEFAULT_STATUS: Status = 'initial';

class Authentication {
  status: Status = DEFAULT_STATUS;

  constructor() {
    makeObservable(this, {
      status: observable,
      setStatus: action,
      reset: action,
    });
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

  setStatus = (status: Status) => {
    this.status = status;
  };

  #handleThirdPartySessionExpiration = () => {
    const wasReloaded = getStateLocally('wasReloaded');

    this.setStatus('invalid-third-party-session');

    if (wasReloaded) {
      return;
    }

    storeStateLocally('wasReloaded', true);

    window.location.reload();
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

    if (
      !window.clientConfig?.canLogout ||
      window.clientConfig?.isLoginDelegated
    ) {
      this.#handleThirdPartySessionExpiration();
      return;
    }

    this.setStatus('logged-out');
  };

  activateSession = () => {
    this.setStatus('logged-in');
    storeStateLocally('wasReloaded', false);
  };

  disableSession = () => {
    if (
      !window.clientConfig?.canLogout ||
      window.clientConfig?.isLoginDelegated
    ) {
      this.#handleThirdPartySessionExpiration();

      return;
    }

    if (['session-invalid', 'session-expired'].includes(this.status)) {
      return;
    }

    if (this.status === 'initial') {
      this.setStatus('session-invalid');
    } else {
      this.setStatus('session-expired');
    }
  };

  reset = () => {
    this.status = DEFAULT_STATUS;
  };
}

/* istanbul ignore next */
function request(input: string, init?: RequestInit) {
  return fetch(mergePathname(BASENAME, input), {
    ...init,
    credentials: 'include',
    mode: 'cors',
  });
}

const authenticationStore = new Authentication();

export {authenticationStore};
