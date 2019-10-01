/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export default class Poll {
  constructor(pollDelay) {
    this.pollTimer = null;
    this.POLL_DELAY = pollDelay;
  }

  _resetTimer() {
    clearTimeout(this.pollTimer);
  }

  _setTimer(callback) {
    this.pollTimer = setTimeout(() => {
      callback();
    }, this.POLL_DELAY);
  }

  _clearPolling() {
    this.pollTimer && this._resetTimer();
    this.pollTimer = null;
  }

  clear() {
    this._clearPolling();
  }

  start(callback) {
    this._clearPolling();
    this._setTimer(callback);
  }
}
