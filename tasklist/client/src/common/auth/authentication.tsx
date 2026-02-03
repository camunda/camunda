/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeObservable, observable, action} from 'mobx';
import {commonApi} from 'common/api';
import {getClientConfig} from 'common/config/getClientConfig';
import {reactQueryClient} from 'common/react-query/reactQueryClient';
import {request} from 'common/api/request';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {currentUserQueryOptions} from 'common/api/useCurrentUser.query';
import {z} from 'zod';

type Status =
  | 'initial'
  | 'logged-in'
  | 'logged-out'
  | 'session-expired'
  | 'session-invalid'
  | 'invalid-third-party-session';

const DEFAULT_STATUS: Status = 'initial';

const logoutResponseSchema = z.object({
  url: z.url({error: 'no redirect URL provided'}),
});

async function parseRedirectUrl(response: Response): Promise<string> {
  const json = await response.json();
  const result = logoutResponseSchema.parse(json);
  return result.url;
}

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
    const {response, error} = await request(
      commonApi.login({username, password}),
      {
        skipSessionCheck: true,
      },
    );

    if (error === null) {
      this.activateSession();
      await reactQueryClient.ensureQueryData(currentUserQueryOptions);
    }

    return {response, error};
  };

  setStatus = (status: Status) => {
    this.status = status;
  };

  #handleThirdPartySessionExpiration = (redirectUrl?: string) => {
    this.setStatus('invalid-third-party-session');

    const wasReloaded = getStateLocally('wasReloaded');
    if (wasReloaded) {
      return;
    }
    storeStateLocally('wasReloaded', true);

    if (redirectUrl) {
      window.location.href = redirectUrl;
    } else {
      window.location.reload();
    }
  };

  handleLogout = async () => {
    const {response, error} = await request(commonApi.logout(), {
      skipSessionCheck: true,
    });

    if (error !== null) {
      return error;
    }

    reactQueryClient.clear();

    if (!getClientConfig().canLogout || getClientConfig().isLoginDelegated) {
      /*
       * In case an IdP supports RP-initiated logout,
       * its logout endpoint will be returned in a JSON response.
       * For Basic Auth and unsupported IdPs, there will be a 204 response.
       */
      const idpLogoutUrl =
        response.status === 200 ? await parseRedirectUrl(response) : undefined;
      this.#handleThirdPartySessionExpiration(idpLogoutUrl);
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
    if (!getClientConfig().canLogout || getClientConfig().isLoginDelegated) {
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
