/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';
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

decorate(SessionValidation, {
  state: observable,
  reset: action,
  disableUserSession: action,
  enableUserSession: action,
});

export const sessionValidationStore = new SessionValidation();
