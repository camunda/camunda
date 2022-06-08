/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, observable, action} from 'mobx';
import {getUser, login, logout, Credentials} from 'modules/api/authentication';
import {logger} from 'modules/logger';
import {NetworkError} from 'modules/networkError';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {Undefinable} from 'ts-toolbelt/out/Object/Undefinable';

type Permissions = Array<'read' | 'write'>;

type State = {
  status:
    | 'initial'
    | 'logged-in'
    | 'fetching-user-information'
    | 'user-information-fetched'
    | 'logged-out'
    | 'session-expired'
    | 'invalid-initial-session'
    | 'invalid-third-party-session';
  permissions: Permissions;
  displayName: string | null;
  canLogout: boolean;
  userId: string | null;
  salesPlanType: string | null;
  roles: ReadonlyArray<string> | null;
};

const DEFAULT_STATE: State = {
  status: 'initial',
  permissions: ['read', 'write'],
  displayName: null,
  canLogout: false,
  userId: null,
  salesPlanType: null,
  roles: [],
};

class Authentication {
  state: State = {...DEFAULT_STATE};
  constructor() {
    makeObservable(this, {
      state: observable,
      disableSession: action,
      expireSession: action,
      startLoadingUser: action,
      setUser: action,
      reset: action,
      resetUser: action,
      setStatus: action,
      endLogin: action,
    });
  }

  disableSession = () => {
    this.resetUser();

    if (
      !window.clientConfig?.canLogout ||
      window.clientConfig?.isLoginDelegated
    ) {
      this.#handleThirdPartySessionExpiration();

      return;
    }

    this.setStatus('logged-out');
  };

  expireSession = () => {
    this.resetUser();

    if (
      !window.clientConfig?.canLogout ||
      window.clientConfig?.isLoginDelegated
    ) {
      this.#handleThirdPartySessionExpiration();

      return;
    }

    if (this.state.status === 'user-information-fetched') {
      this.setStatus('session-expired');

      return;
    }

    this.setStatus('invalid-initial-session');
  };

  #handleThirdPartySessionExpiration = () => {
    const wasReloaded = getStateLocally()?.wasReloaded;

    this.setStatus('invalid-third-party-session');

    if (wasReloaded) {
      return;
    }

    storeStateLocally({
      wasReloaded: true,
    });

    window.location.reload();
  };

  handleLogin = async (credentials: Credentials): Promise<Error | void> => {
    try {
      const response = await login(credentials);

      if (!response.ok) {
        return new NetworkError('Could not login credentials', response);
      }

      this.endLogin();

      return;
    } catch (error) {
      logger.error(error);

      return new Error('Could not login credentials');
    }
  };

  endLogin = () => {
    this.state.status = 'logged-in';
  };

  authenticate = async (): Promise<void | Error> => {
    this.startLoadingUser();

    try {
      const response = await getUser();

      if (!response.ok) {
        this.expireSession();

        return new Error('Could not fetch user information');
      }

      this.setUser(await response.json());
    } catch (error) {
      this.disableSession();

      logger.error(error);

      return new Error('Could not fetch user information');
    }
  };

  startLoadingUser = () => {
    this.state.status = 'fetching-user-information';
  };

  setUser = ({
    displayName,
    permissions,
    canLogout,
    userId,
    salesPlanType,
    roles,
  }: Undefinable<
    Pick<
      State,
      | 'displayName'
      | 'permissions'
      | 'canLogout'
      | 'userId'
      | 'salesPlanType'
      | 'roles'
    >,
    'permissions'
  >) => {
    storeStateLocally({
      wasReloaded: false,
    });

    this.state.status = 'user-information-fetched';
    this.state.displayName = displayName;
    this.state.canLogout = canLogout;
    this.state.userId = userId;
    this.state.salesPlanType = salesPlanType;
    this.state.roles = roles ?? [];
    this.state.permissions = permissions ?? DEFAULT_STATE.permissions;
  };

  handleLogout = async () => {
    try {
      const response = await logout();

      if (!response.ok) {
        return new Error('Could not logout');
      }

      this.disableSession();
    } catch (error) {
      logger.error(error);

      return new Error('Could not logout');
    }
  };

  hasPermission = (scopes: Permissions) => {
    return this.state.permissions.some((permission) =>
      scopes.includes(permission)
    );
  };

  handleThirdPartySessionSuccess = () => {
    if (this.state.status === 'invalid-third-party-session') {
      this.authenticate();
    }
  };

  setStatus = (status: State['status']) => {
    this.state.status = status;
  };

  resetUser = () => {
    this.state.displayName = DEFAULT_STATE.displayName;
    this.state.canLogout = DEFAULT_STATE.canLogout;
    this.state.permissions = DEFAULT_STATE.permissions;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const authenticationStore = new Authentication();
export type {Permissions};
