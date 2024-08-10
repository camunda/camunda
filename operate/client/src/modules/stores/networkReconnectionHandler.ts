/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, makeObservable} from 'mobx';

class NetworkReconnectionHandler {
  boundCallback?: Function;
  constructor() {
    makeObservable(this, {reset: action.bound});
  }
  handleReconnection = () => {
    this.boundCallback?.();
  };
  retryOnConnectionLost(callback: (...params: any[]) => any) {
    this.boundCallback = callback;
    return (...params: any[]) => {
      window.removeEventListener('online', this.handleReconnection);
      window.addEventListener('online', this.handleReconnection);
      this.boundCallback = callback.bind(this, ...params);
      return callback(...params);
    };
  }
  reset() {
    window.removeEventListener('online', this.handleReconnection);
  }
}
export {NetworkReconnectionHandler};
