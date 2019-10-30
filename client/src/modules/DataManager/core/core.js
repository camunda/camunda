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
  fetchWorkflowInstance,
  fetchWorkflowInstances,
  fetchWorkflowInstanceIncidents,
  fetchVariables,
  applyOperation
} from 'modules/api/instances';

import {fetchActivityInstancesTree} from 'modules/api/activityInstances';

import {fetchWorkflowXML} from 'modules/api/diagram';
import {fetchEvents} from 'modules/api/events';

import {parseDiagramXML} from 'modules/utils/bpmn';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

import RequestCache from '../cache';
import Publisher from '../publisher';
import Poll from '../poll';

const {
  LOAD_CORE_STATS,
  LOAD_LIST_INSTANCES,
  LOAD_STATE_STATISTICS,
  LOAD_STATE_DEFINITIONS
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

  /** Wrapped API calls */

  applyOperation(instanceId, payload) {
    const operationLevel = payload.incidentId ? 'INCIDENT' : 'INSTANCE';

    this.publisher.pubLoadingStates(
      `OPERATION_APPLIED_${operationLevel}_${instanceId}`,
      () => applyOperation(instanceId, payload)
    );
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

  async getWorkflowInstancesBySelection(params, topic, selectionId) {
    const cachedParams = this.cache.update(
      'workflowInstancesBySelection',
      fetchWorkflowInstancesBySelection,
      params
    );

    this.publisher.publish(topic, {state: LOADING_STATE.LOADING});
    const response = await fetchWorkflowInstancesBySelection(cachedParams);

    if (response.error) {
      this.publisher.publish(topic, {
        state: LOADING_STATE.LOAD_FAILED,
        response
      });
    } else {
      this.publisher.publish(topic, {
        state: LOADING_STATE.LOADED,
        response: {...response, selectionId}
      });
    }
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

  /** Update Data */

  // fetches the data again for all passed endpoints and publishes loading states and result to passed topic
  update({endpoints, topic, staticData}) {
    this.publisher.publish(topic, {
      state: LOADING_STATE.LOADING
    });

    Promise.all([
      ...endpoints.map(endpointName => {
        const cachedEndpoints = this.cache.getEndpointsbyNames([endpointName]);
        if (cachedEndpoints[endpointName]) {
          const {params, apiCall} = cachedEndpoints[endpointName];
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
              const responseName = endpoints[index];
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
