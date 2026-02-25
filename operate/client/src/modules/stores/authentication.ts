/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeObservable, observable, action} from 'mobx';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {currentUserQueryOptions} from 'modules/queries/useCurrentUser';
import {reactQueryClient} from 'modules/react-query/reactQueryClient';
import {request} from 'modules/request';
import {getClientConfig} from 'modules/utils/getClientConfig';
import z from 'zod';

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
    try {
      const response = await request(
        {
          url: '/login',
          method: 'POST',
          body: new URLSearchParams([
            ['username', username],
            ['password', password],
          ]).toString(),
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        },
        {
          skipSessionCheck: true,
        },
      );

      if (response.ok) {
        this.activateSession();
        await reactQueryClient.ensureQueryData(currentUserQueryOptions);

        return;
      }

      return response;
    } catch (error) {
      return error;
    }
  };

  setStatus = (status: Status) => {
    this.status = status;
  };

  #handleThirdPartySessionExpiration = (redirectUrl?: string) => {
    this.setStatus('invalid-third-party-session');

    const wasReloaded = getStateLocally().wasReloaded;
    if (wasReloaded) {
      return;
    }
    storeStateLocally({wasReloaded: true});

    if (redirectUrl) {
      window.location.href = redirectUrl;
    } else {
      window.location.reload();
    }
  };

  handleLogout = async () => {
    try {
      const response = await request(
        {
          url: '/logout',
          method: 'POST',
          headers: {
            Accept: 'application/json, text/plain',
          },
        },
        {
          skipSessionCheck: true,
        },
      );

      if (!response.ok) {
        return new Error('Failed to logout');
      }

      reactQueryClient.clear();
      const clientConfig = getClientConfig();

      if (!clientConfig.canLogout || clientConfig.isLoginDelegated) {
        /*
         * In case an IdP supports RP-initiated logout,
         * its logout endpoint will be returned in a JSON response.
         * For Basic Auth and unsupported IdPs, there will be a 204 response.
         */
        const idpLogoutUrl =
          response.status === 200
            ? await parseRedirectUrl(response)
            : undefined;
        this.#handleThirdPartySessionExpiration(idpLogoutUrl);
        return;
      }

      this.setStatus('logged-out');
      return;
    } catch (error) {
      return error;
    }
  };

  activateSession = () => {
    this.setStatus('logged-in');
    storeStateLocally({wasReloaded: false});
  };

  disableSession = () => {
    const clientConfig = getClientConfig();

    if (!clientConfig.canLogout || clientConfig.isLoginDelegated) {
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
