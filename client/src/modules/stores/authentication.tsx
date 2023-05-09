/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, observable, action} from 'mobx';
import {resetApolloStore} from 'modules/apollo-client';
import {request} from 'modules/request';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

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
    const {response, error} = await request(Endpoints.Login, {
      method: 'POST',
      body: new URLSearchParams({username, password}).toString(),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    if (error === null) {
      this.activateSession();
    }

    return {response, error};
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
    const {error} = await request(Endpoints.Logout, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (error !== null) {
      return error;
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
    return;
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

const authenticationStore = new Authentication();

export {authenticationStore};
