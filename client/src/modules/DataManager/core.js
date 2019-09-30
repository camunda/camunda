/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  fetchWorkflowInstancesStatistics,
  fetchWorkflowInstancesBySelection,
  fetchWorkflowCoreStatistics,
  fetchWorkflowInstancesByIds,
  fetchWorkflowInstances
} from 'modules/api/instances';

import {fetchWorkflowXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

import {Poll} from './poll';

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
    this.poll = new Poll();
    this.updatableData = {};
  }

  async _publishLoadingState(topic, request, params) {
    this.publish(topic, {state: this.loadingStates.LOADING});

    const response = await request(params);

    if (response.error) {
      this.publish(topic, {
        state: this.loadingStates.LOAD_FAILED,
        response
      });
    } else {
      this.publish(topic, {state: this.loadingStates.LOADED, response});
    }

    return response;
  }

  manageUpdatableData(type, {params, endpoint}) {
    if (params) {
      this.updatableData = {...this.updatableData, [type]: {params, endpoint}};
      return params;
    } else {
      return this.updatableData[type];
    }
  }

  /** Wrapped API calls */

  async getCoreStatistics() {
    const endpoint = fetchWorkflowCoreStatistics;
    let requestParams = this.manageUpdatableData('coreStatistics', {
      params: {},
      endpoint
    });
    return await this._publishLoadingState(
      SUBSCRIPTION_TOPIC.LOAD_CORE_STATS,
      endpoint,
      requestParams
    );
  }

  async getWorkflowInstances(params) {
    const endpoint = fetchWorkflowInstances;
    let requestParams = this.manageUpdatableData('workflowInstances', {
      params,
      endpoint
    });
    return await this._publishLoadingState(
      this.registeredTopics.LOAD_LIST_INSTANCES,
      endpoint,
      requestParams
    );
  }

  async getWorkflowInstancesStatistics(params) {
    const endpoint = fetchWorkflowInstancesStatistics;
    let requestParams = this.manageUpdatableData('statistics', {
      params,
      endpoint
    });
    return (
      requestParams &&
      (await this._publishLoadingState(
        this.registeredTopics.LOAD_STATE_STATISTICS,
        endpoint,
        requestParams
      ))
    );
  }

  async getWorkflowInstancesBySelection(params, topic, selectionId) {
    const endpoint = fetchWorkflowInstancesBySelection;
    let requestParams = params;
    this.publish(topic, {state: this.loadingStates.LOADING});
    const response = await endpoint(requestParams);

    if (response.error) {
      this.publish(topic, {
        state: this.loadingStates.LOAD_FAILED,
        response
      });
    } else {
      this.publish(topic, {
        state: this.loadingStates.LOADED,
        response: {...response, selectionId}
      });
    }
  }

  // helper function used to create a single function
  // which can be passed to the publishLoadingState function
  async fetchDiagramModel(params) {
    const xml = await fetchWorkflowXML(params);
    return await parseDiagramXML(xml);
  }

  async getWorkflowXML(params) {
    const endpoint = this.fetchDiagramModel;

    let requestParams = this.manageUpdatableData('workflowXML', {
      params,
      endpoint
    });
    return await this._publishLoadingState(
      this.registeredTopics.LOAD_STATE_DEFINITIONS,
      endpoint,
      requestParams
    );
  }

  async getWorkflowInstancesByIds(params, topic) {
    const endpoint = fetchWorkflowInstancesByIds;
    let requestParams = this.manageUpdatableData(
      'fetchWorkflowInstancesByIds',
      {
        params,
        endpoint
      }
    );
    return await this._publishLoadingState(topic, endpoint, requestParams);
  }

  /** Update Data */

  // Retruns the names of all api requests which have been executed before, so they can be called again.
  getCachedRequestEndpoints() {
    return Object.keys(this.updatableData).reduce((acc, name) => {
      return {...acc, [name]: name};
    }, {});
  }

  // fetches the data again for all passed endpoints and publishes loading states and result to passed topic
  async update({endpoints, topic, staticData}) {
    this.publish(topic, {
      state: this.loadingStates.LOADING
    });

    Promise.all([
      ...endpoints.map(endpointName => {
        const {endpoint, params} = this.updatableData[endpointName];
        return endpoint(params);
      })
    ])
      .then(response => {
        const publishData = {
          state: this.loadingStates.LOADED,
          response: {
            ...response.reduce((acc, response, index) => {
              const responseName = endpoints[index];
              return {...acc, [responseName]: response};
            }, {}),
            ...staticData
          }
        };
        this.publish(topic, publishData);
      })
      .catch(error => {
        this.publish(topic, {
          state: this.loadingStates.LOAD_FAILED,
          error
        });
      });
  }
}
