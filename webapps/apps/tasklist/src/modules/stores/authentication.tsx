/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeObservable, observable, action} from 'mobx';
import {api} from 'modules/api';
import {reactQueryClient} from 'modules/react-query/reactQueryClient';
import {request} from 'modules/request';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

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
    const {response, error} = await request(api.login({username, password}), {
      skipSessionCheck: true,
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
    const {error} = await request(api.logout(), {
      skipSessionCheck: true,
    });

    if (error !== null) {
      return error;
    }

    reactQueryClient.clear();

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
