/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action} from 'mobx';
import {Notification} from 'modules/notifications';

type State = {
  isSessionValid: boolean;
  notification: Notification | undefined;
};

const DEFAULT_STATE: State = {
  isSessionValid: false,
  notification: undefined,
};

class SessionValidation {
  state: State = {...DEFAULT_STATE};
  constructor() {
    makeObservable(this, {
      state: observable,
      disableUserSession: action,
      enableUserSession: action,
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

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const sessionValidationStore = new SessionValidation();
