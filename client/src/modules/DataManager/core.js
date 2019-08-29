/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  fetchWorkflowInstancesStatistics,
  fetchWorkflowInstances
} from 'modules/api/instances';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

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
  constructor() {
    super();
    this.loadingStates = LOADING_STATE;
    this.registeredTopics = SUBSCRIPTION_TOPIC;
  }

  async _publishLoadingState(topic, request, params) {
    this.publish(topic, {state: 'LOADING'});

    const response = await request(params);

    if (response.error) {
      this.publish(topic, {
        state: 'LOAD_FAILED',
        response
      });
    } else {
      this.publish(topic, {state: 'LOADED', response});
    }

    return response;
  }

  // Wrapped API calls

  async getWorkflowInstances(params) {
    return await this._publishLoadingState(
      this.registeredTopics.LOAD_STATE_INSTANCES,
      fetchWorkflowInstances,
      params
    );
  }

  async getWorkflowInstancesStatistics(params) {
    return await this._publishLoadingState(
      this.registeredTopics.LOAD_STATE_STATISTICS,
      fetchWorkflowInstancesStatistics,
      params
    );
  }
  // helper function used to create a single function
  // which can be passed to the publishLoadingState function
  async fetchDiagramModel(params) {
    const xml = await fetchWorkflowXML(params);
    return await parseDiagramXML(xml);
  }

  async getWorkflowXML(params) {
    return await this._publishLoadingState(
      this.registeredTopics.LOAD_STATE_DEFINITIONS,
      this.fetchDiagramModel,
      params
    );
  }
}
