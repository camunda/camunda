/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action} from 'mobx';
import {getUser, login, logout, Credentials} from 'modules/api/authentication';
import {logger} from 'modules/logger';
import {NetworkError} from 'modules/networkError';
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
    | 'invalid-initial-session';
  permissions: Permissions;
  displayName: string | undefined;
  canLogout: boolean;
};

const DEFAULT_STATE: State = {
  status: 'initial',
  permissions: ['read', 'write'],
  displayName: undefined,
  canLogout: false,
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
      endLogin: action,
    });
  }

  disableSession = () => {
    this.state.status = 'logged-out';
    this.state.displayName = DEFAULT_STATE.displayName;
    this.state.canLogout = DEFAULT_STATE.canLogout;
    this.state.permissions = DEFAULT_STATE.permissions;
  };

  expireSession = () => {
    if (window.clientConfig?.organizationId) {
      return;
    }

    if (this.state.status === 'user-information-fetched') {
      this.state.status = 'session-expired';
    } else {
      this.state.status = 'invalid-initial-session';
    }

    this.state.displayName = DEFAULT_STATE.displayName;
    this.state.canLogout = DEFAULT_STATE.canLogout;
    this.state.permissions = DEFAULT_STATE.permissions;
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
  }: Undefinable<
    Pick<State, 'displayName' | 'permissions' | 'canLogout'>,
    'permissions'
  >) => {
    this.state.status = 'user-information-fetched';
    this.state.displayName = displayName;
    this.state.canLogout = canLogout;
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

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const authenticationStore = new Authentication();
export type {Permissions};
