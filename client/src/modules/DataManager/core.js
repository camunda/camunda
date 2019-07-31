/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {LOADING_STATE} from 'modules/constants';

export class Publisher {
  constructor(subscriptionTopics) {
    this.subscriptions = {};
    this.registeredTopics = subscriptionTopics;
  }

  printWarning(topic, action) {
    console.warn(
      `you just ${
        action === 'subscribe' ? 'subscribed' : 'published'
      } to the unregisted topic ${topic}, probably no one is publishing to it`
    );
  }

  subscribe(obj) {
    return Object.entries(obj).forEach(([topic, callback]) => {
      !this.registeredTopics[topic] && this.printWarning(topic, 'subscribe');

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

  publish(topic, value) {
    !this.registeredTopics[topic] && this.printWarning(topic, 'publish');

    this.subscriptions[topic] &&
      this.subscriptions[topic].forEach(handle => {
        handle(value);
      });
  }
}

export class DataManager extends Publisher {
  async _publishLoadingState(topic, request) {
    this.publish(topic, {state: LOADING_STATE.LOADING});

    const response = await request();

    if (response.error) {
      this.publish(topic, {
        state: LOADING_STATE.LOAD_FAILED,
        response
      });
    } else {
      this.publish(topic, {state: LOADING_STATE.LOADED, response});
    }

    return response;
  }
}
