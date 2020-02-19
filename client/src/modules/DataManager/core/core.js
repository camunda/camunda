/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  fetchWorkflowInstancesStatistics,
  fetchWorkflowCoreStatistics,
  fetchWorkflowInstancesByIds,
  fetchWorkflowInstance,
  fetchWorkflowInstances,
  fetchWorkflowInstanceIncidents,
  fetchVariables,
  applyBatchOperation,
  applyOperation
} from 'modules/api/instances';

import {
  fetchInstancesByWorkflow,
  fetchIncidentsByError
} from 'modules/api/incidents';

import {fetchActivityInstancesTree} from 'modules/api/activityInstances';

import {fetchWorkflowXML} from 'modules/api/diagram';
import {fetchEvents} from 'modules/api/events';

import {fetchBatchOperations} from 'modules/api/batchOperations';

import {parseDiagramXML} from 'modules/utils/bpmn';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

import RequestCache from '../cache';
import Publisher from '../publisher';
import Poll from '../poll';

const {
  LOAD_CORE_STATS,
  LOAD_LIST_INSTANCES,
  LOAD_STATE_STATISTICS,
  LOAD_STATE_DEFINITIONS,
  LOAD_BATCH_OPERATIONS
} = SUBSCRIPTION_TOPIC;

export class DataManager {
  constructor() {
    this.poll = new Poll(5000);
    this.publisher = new Publisher(SUBSCRIPTION_TOPIC, LOADING_STATE);
    this.cache = new RequestCache();
  }

  // Public api to interact with the publisher.
  subscribe(subscriptions) {
    this.publisher.subscribe(subscriptions);
  }

  unsubscribe(subscriptions) {
    this.publisher.unsubscribe(subscriptions);
  }

  subscriptions() {
    return this.publisher.subscriptions;
  }

  /** Wrapped API calls */
  applyOperation(id, payload) {
    const typeStrings = payload.operationType.split('_');
    const operationType = typeStrings[typeStrings.length - 1];

    this.publisher.pubLoadingStates(
      `OPERATION_APPLIED_${operationType}_${id}`,
      () => applyOperation(id, payload)
    );
  }

  applyBatchOperation(operationType, query) {
    applyBatchOperation(operationType, query);
  }

  fetchAndPublish(topic, apiCall, params, staticContent) {
    const cachedParams = this.cache.update(topic, apiCall, params);
    this.publisher.pubLoadingStates(
      topic,
      () => apiCall(cachedParams),
      staticContent
    );
  }

  getVariables(instanceId, scopeId) {
    this.fetchAndPublish(SUBSCRIPTION_TOPIC.LOAD_VARIABLES, fetchVariables, {
      instanceId,
      scopeId
    });
  }

  getActivityInstancesTreeData(instance) {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_INSTANCE_TREE,
      fetchActivityInstancesTree,
      instance.id,
      instance
    );
  }

  getEvents(instanceId) {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_EVENTS,
      fetchEvents,
      instanceId
    );
  }

  getIncidents(instance) {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_INCIDENTS,
      fetchWorkflowInstanceIncidents,
      instance
    );
  }

  getWorkflowInstance(instanceId) {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_INSTANCE,
      fetchWorkflowInstance,
      instanceId
    );
  }

  getInstancesByWorkflow() {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_INSTANCES_BY_WORKFLOW,
      fetchInstancesByWorkflow
    );
  }

  getIncidentsByError() {
    this.fetchAndPublish(
      SUBSCRIPTION_TOPIC.LOAD_INCIDENTS_BY_ERROR,
      fetchIncidentsByError
    );
  }

  getWorkflowCoreStatistics() {
    this.fetchAndPublish(LOAD_CORE_STATS, fetchWorkflowCoreStatistics, {});
  }

  getWorkflowInstances(params) {
    this.fetchAndPublish(LOAD_LIST_INSTANCES, fetchWorkflowInstances, params);
  }

  getWorkflowInstancesStatistics(params) {
    this.fetchAndPublish(
      LOAD_STATE_STATISTICS,
      fetchWorkflowInstancesStatistics,
      params
    );
  }

  getWorkflowXML(params, staticContent) {
    const fetchDiagramModel = async params => {
      const xml = await fetchWorkflowXML(params);
      return await parseDiagramXML(xml);
    };

    const cachedParams = this.cache.update(
      LOAD_STATE_DEFINITIONS,
      fetchDiagramModel,
      params
    );

    this.publisher.pubLoadingStates(
      LOAD_STATE_DEFINITIONS,
      () => fetchDiagramModel(cachedParams),
      staticContent
    );
  }

  getWorkflowInstancesByIds(params, topic) {
    this.fetchAndPublish(topic, fetchWorkflowInstancesByIds, params);
  }

  getBatchOperations(params) {
    this.fetchAndPublish(LOAD_BATCH_OPERATIONS, fetchBatchOperations, params);
  }

  /** Update Data */

  // fetches the data again for all passed endpoints and publishes loading states and result to passed topic
  update({endpoints, topic, staticData}) {
    this.publisher.publish(topic, {
      state: LOADING_STATE.LOADING
    });

    Promise.all([
      ...endpoints.map(({name}) => {
        const cachedEndpoints = this.cache.getEndpointsbyNames([name]);
        if (cachedEndpoints[name]) {
          const {params, apiCall} = cachedEndpoints[name];

          return apiCall(params);
        }
        return null;
      })
    ])
      .then(response => {
        const publishData = {
          state: LOADING_STATE.LOADED,
          response: {
            ...response.reduce((acc, response, index) => {
              const responseName = endpoints[index].name;
              return {...acc, [responseName]: response};
            }, {}),
            ...staticData
          }
        };
        this.publisher.publish(topic, publishData);
      })
      .catch(error => {
        this.publisher.publish(topic, {
          state: LOADING_STATE.LOAD_FAILED,
          error
        });
      });
  }
}
