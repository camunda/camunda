/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export class Publisher {
  constructor(SUBSCRIPTION_TOPIC) {
    this.subscriptions = {};
    this.registeredTopics = SUBSCRIPTION_TOPIC;
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
  constructor(LOADING_STATE, SUBSCRIPTION_TOPIC) {
    super();
    this.loadingStates = LOADING_STATE;
    this.registeredTopics = SUBSCRIPTION_TOPIC;
  }

  async _publishLoadingState(topic, request) {
    this.publish(topic, {state: this.loadingStates['LOADING']});

    const response = await request();

    if (response.error) {
      this.publish(topic, {
        state: this.loadingStates['LOAD_FAILED'],
        response
      });
    } else {
      this.publish(topic, {state: this.loadingStates['LOADED'], response});
    }

    return response;
  }
}
