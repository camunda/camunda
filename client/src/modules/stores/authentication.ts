/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action} from 'mobx';
import {Notification} from 'modules/notifications';

type Roles = Array<'view' | 'edit'>;

type State = {
  isSessionValid: boolean;
  notification: Notification | undefined;
  roles: Roles;
};

const DEFAULT_STATE: State = {
  isSessionValid: false,
  notification: undefined,
  roles: ['view', 'edit'],
};

class Authentication {
  state: State = {...DEFAULT_STATE};
  constructor() {
    makeObservable(this, {
      state: observable,
      disableUserSession: action,
      enableUserSession: action,
      setRoles: action,
      reset: action,
    });
  }

  disableUserSession = (notification: Notification | undefined) => {
    this.state.isSessionValid = false;
    this.state.notification = notification;
  };

  enableUserSession = () => {
    this.state.isSessionValid = true;
    this.state.notification?.remove();
    this.state.notification = undefined;
  };

  hasPermission = (scopes: Roles) => {
    return this.state.roles.some((role) => scopes.includes(role));
  };

  setRoles = (roles: Roles | undefined) => {
    this.state.roles = roles ?? DEFAULT_STATE.roles;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const authenticationStore = new Authentication();
export type {Roles};
