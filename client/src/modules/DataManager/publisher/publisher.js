/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export default class Publisher {
  constructor(subscriptionTopics, loadingStates) {
    this.subscriptions = {};
    this.registeredTopics = subscriptionTopics;
    this.loadingStates = loadingStates;
  }

  printWarning(topic, action) {
    const getActionTerms = action =>
      action === 'subscribe'
        ? {action: 'subscribed', counterpart: 'publishes'}
        : {action: 'published', counterpart: 'subscribed'};

    const context = getActionTerms(action);
    console.warn(
      `you ${context.action} to the topic ${topic}, no one ${context.counterpart} to.`
    );
  }

  subscribe(obj) {
    return Object.entries(obj).forEach(([topic, callback]) => {
      this.subscriptions = this.subscriptions[topic]
        ? {
            ...this.subscriptions,
            [topic]: [...this.subscriptions[topic], callback]
          }
        : {...this.subscriptions, [topic]: [callback]};
    });
  }

  unsubscribe(subscriptions) {
    Object.entries(subscriptions).forEach(([topic, callback]) => {
      const callbackIndex = this.subscriptions[topic].indexOf(callback);
      this.subscriptions[topic].splice(callbackIndex, callbackIndex + 1);

      if (!this.subscriptions[topic].length) {
        delete this.subscriptions[topic];
      }
    });
  }

  publish(topic, value, staticContent) {
    this.subscriptions[topic] &&
      this.subscriptions[topic].forEach(handle => {
        if (staticContent) {
          handle(value, staticContent);
        } else {
          handle(value);
        }
      });
  }

  async pubLoadingStates(topic, callback, staticContent) {
    this.publish(topic, {state: this.loadingStates.LOADING});

    const response = await callback();

    this.publish(topic, {
      state: !!response.error
        ? this.loadingStates.LOAD_FAILED
        : this.loadingStates.LOADED,
      response,
      ...(!!staticContent && {staticContent: staticContent})
    });
  }
}
