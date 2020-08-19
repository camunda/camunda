/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  fetchWorkflowInstancesStatistics,
  fetchWorkflowInstancesByIds,
  fetchWorkflowInstance,
  fetchWorkflowInstances,
  fetchWorkflowInstanceIncidents,
  applyBatchOperation,
  applyOperation,
} from 'modules/api/instances';

import {fetchEvents} from 'modules/api/events';

import {fetchBatchOperations} from 'modules/api/batchOperations';

import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

import RequestCache from '../cache';
import Publisher from '../publisher';
import Poll from '../poll';

const {
  LOAD_LIST_INSTANCES,
  LOAD_STATE_STATISTICS,
  LOAD_BATCH_OPERATIONS,
  CREATE_BATCH_OPERATION,
  OPERATION_APPLIED,
} = SUBSCRIPTION_TOPIC;

export class DataManager {
  constructor() {
    this.poll = new Poll(5000);
    this.publisher = new Publisher(SUBSCRIPTION_TOPIC, LOADING_STATE);
    this.cache = new RequestCache();
  }

  // Public api to interact with the publisher.
  subscribe = (subscriptions) => {
    this.publisher.subscribe(subscriptions);
  };

  unsubscribe = (subscriptions) => {
    this.publisher.unsubscribe(subscriptions);
  };

  subscriptions = () => {
    return this.publisher.subscriptions;
  };

  /** Wrapped API calls */
  applyOperation = (id, payload) => {
    const typeStrings = payload.operationType.split('_');
    const operationType = typeStrings[typeStrings.length - 1];

    this.publisher.pubLoadingStates(
      [`OPERATION_APPLIED_${operationType}_${id}`, OPERATION_APPLIED],
      () => applyOperation(id, payload)
    );
  };

  applyBatchOperation = async (operationType, query) => {
    this.publisher.pubLoadingStates(CREATE_BATCH_OPERATION, () =>
      applyBatchOperation(operationType, query)
    );
  };

  fetchAndPublish = (topic, apiCall, params, staticContent) => {
    const cachedParams = this.cache.update(topic, apiCall, params);
    this.publisher.pubLoadingStates(
      topic,
      () => apiCall(cachedParams),
      staticContent
    );
  };

  getEvents = (instanceId) => {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_EVENTS,
      fetchEvents,
      instanceId
    );
  };

  getIncidents = (instance) => {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_INCIDENTS,
      fetchWorkflowInstanceIncidents,
      instance
    );
  };

  getWorkflowInstance = (instanceId) => {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_INSTANCE,
      fetchWorkflowInstance,
      instanceId
    );
  };

  getWorkflowInstances = (params) => {
    this.fetchAndPublish(LOAD_LIST_INSTANCES, fetchWorkflowInstances, params);
  };

  getWorkflowInstancesStatistics = (params) => {
    this.fetchAndPublish(
      LOAD_STATE_STATISTICS,
      fetchWorkflowInstancesStatistics,
      params
    );
  };

  getWorkflowInstancesByIds = (params, topic) => {
    this.fetchAndPublish(topic, fetchWorkflowInstancesByIds, params);
  };

  getBatchOperations = (params) => {
    this.fetchAndPublish(LOAD_BATCH_OPERATIONS, fetchBatchOperations, params);
  };

  /** Update Data */

  // fetches the data again for all passed endpoints and publishes loading states and result to passed topic
  update = ({endpoints, topic, staticData}) => {
    this.publisher.publish(topic, {
      state: LOADING_STATE.LOADING,
    });

    Promise.all([
      ...endpoints.map(({name}) => {
        const cachedEndpoints = this.cache.getEndpointsbyNames([name]);
        if (cachedEndpoints[name]) {
          const {params, apiCall} = cachedEndpoints[name];

          return apiCall(params);
        }
        return null;
      }),
    ])
      .then((response) => {
        const publishData = {
          state: LOADING_STATE.LOADED,
          response: {
            ...response.reduce((acc, response, index) => {
              const responseName = endpoints[index].name;
              return {...acc, [responseName]: response};
            }, {}),
            ...staticData,
          },
        };
        this.publisher.publish(topic, publishData);
      })
      .catch((error) => {
        this.publisher.publish(topic, {
          state: LOADING_STATE.LOAD_FAILED,
          error,
        });
      });
  };
}
