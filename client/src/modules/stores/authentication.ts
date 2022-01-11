/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action} from 'mobx';
import {Notification} from 'modules/notifications';

type Permissions = Array<'read' | 'write'>;

type State = {
  isSessionValid: boolean;
  notification: Notification | undefined;
  permissions: Permissions;
  displayName: string | undefined;
  canLogout: boolean;
};

const DEFAULT_STATE: State = {
  isSessionValid: false,
  notification: undefined,
  permissions: ['read', 'write'],
  displayName: undefined,
  canLogout: false,
};

class Authentication {
  state: State = {...DEFAULT_STATE};
  constructor() {
    makeObservable(this, {
      state: observable,
      disableUserSession: action,
      enableUserSession: action,
      reset: action,
    });
  }

  disableUserSession = (notification: Notification | undefined) => {
    this.state.isSessionValid = false;
    this.state.notification = notification;
  };

  enableUserSession = ({
    displayName,
    permissions,
    canLogout,
  }: {
    displayName: string;
    permissions?: Permissions;
    canLogout: boolean;
  }) => {
    this.state.isSessionValid = true;
    this.state.displayName = displayName;
    this.state.canLogout = canLogout;
    this.state.permissions = permissions ?? DEFAULT_STATE.permissions;

    this.state.notification?.remove();
    this.state.notification = undefined;
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
