/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instances} from 'modules/stores/instances';

export default class Publisher {
  constructor(subscriptionTopics, loadingStates) {
    this.subscriptions = {};
    this.registeredTopics = subscriptionTopics;
    this.loadingStates = loadingStates;
  }

  printWarning(topic, action) {
    const getActionTerms = (action) =>
      action === 'subscribe'
        ? {action: 'subscribed', counterpart: 'publishes'}
        : {action: 'published', counterpart: 'subscribed'};

    const context = getActionTerms(action);
    console.warn(
      `you ${context.action} to the topic ${topic}, no one ${context.counterpart} to.`
    );
  }

  subscribe(subscriptions) {
    return Object.entries(subscriptions).forEach(([topic, callback]) => {
      if (Array.isArray(this.subscriptions[topic])) {
        this.subscriptions[topic] = [...this.subscriptions[topic], callback];
      } else {
        this.subscriptions[topic] = [callback];
      }
    });
  }

  unsubscribe(subscriptions) {
    Object.entries(subscriptions).forEach(([topic, callback]) => {
      if (Array.isArray(this.subscriptions[topic])) {
        this.subscriptions[topic] = this.subscriptions[topic].filter(
          (subcscription) => {
            return subcscription !== callback;
          }
        );
      }
    });
  }

  publish(topic, value, staticContent) {
    if (Array.isArray(this.subscriptions[topic])) {
      this.subscriptions[topic].forEach((handle) => {
        if (staticContent) {
          handle(value, staticContent);
        } else {
          handle(value);
        }
      });
    }

    if (topic === 'LOAD_LIST_INSTANCES' && value.state === 'LOADED') {
      const {workflowInstances, totalCount} = value.response;
      instances.setInstances({
        workflowInstances,
        filteredInstancesCount: totalCount,
      });
    }
    if (topic === 'REFRESH_AFTER_OPERATION' && value.state === 'LOADED') {
      const {
        workflowInstances,
        totalCount,
      } = value.response.LOAD_LIST_INSTANCES;
      instances.setInstances({
        workflowInstances,
        filteredInstancesCount: totalCount,
      });
    }
  }

  async pubLoadingStates(topics, callback, staticContent) {
    if (typeof topics !== 'string' && !Array.isArray(topics)) {
      throw new Error('Unexpected argument type (topics)');
    }

    const disptachLoading = (topic) => {
      this.publish(topic, {state: this.loadingStates.LOADING});
    };

    const dispatchResponse = (topic) => {
      this.publish(topic, {
        state: !!response.error
          ? this.loadingStates.LOAD_FAILED
          : this.loadingStates.LOADED,
        response,
        ...(!!staticContent && {staticContent}),
      });
    };

    if (typeof topics === 'string') {
      disptachLoading(topics);
    } else {
      topics.forEach(disptachLoading);
    }

    const response = await callback();

    if (typeof topics === 'string') {
      dispatchResponse(topics);
    } else {
      topics.forEach(dispatchResponse);
    }
  }
}
