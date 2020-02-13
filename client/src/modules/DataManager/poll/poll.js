/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/**
 * This class exposes methods for registering and unregistering callbacks
 * which will then be called in a given interval.
 */
export default class Poll {
  constructor(pollDelay) {
    this.pollTimer = null;
    this.pollDelay = pollDelay;
    this.callbacks = {};
  }

  register = (topic, callback) => {
    this.callbacks[topic] = callback;
    this._start();
  };

  unregister = topic => {
    delete this.callbacks[topic];
    if (this.callbacks.length === 0) {
      this._stop();
    }
  };

  _fire = () => {
    Object.values(this.callbacks).forEach(callback => {
      callback();
    });
  };

  _start() {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(this._fire, this.pollDelay);
  }

  _stop() {
    if (!this.pollTimer) return;
    clearInterval(this.pollTimer);
  }
}
