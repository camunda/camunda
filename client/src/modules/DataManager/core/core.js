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
    this.publisher = new Publisher(SUBSCRIPTION_TOPIC);
    this.cache = new RequestCache();
  }

  // Public api to interact with the publisher.
  subscribe(subscriptions) {
    this.publisher.subscribe(subscriptions);
  }

  unsubscribe(subscriptions) {
    this.publisher.unsubscribe(subscriptions);
  }

  _updateRequestCache(name, apiCall, params) {
    if (!params) {
      // Required when single DM 'get' methods are called without the params required to do the api request.
      //In this case the last used once are used again.
      return this.cache.getEndpointsbyNames([name]).params;
    } else {
      this.cache.set(name, {
        params,
        apiCall
      });
      return params;
    }
  }

  async _pubLoadingStates(topic, callback) {
    this.publisher.publish(topic, {state: LOADING_STATE.LOADING});

    const response = await callback();

    if (response.error) {
      this.publisher.publish(topic, {
        state: LOADING_STATE.LOAD_FAILED,
        response
      });
    } else {
      this.publisher.publish(topic, {
        state: LOADING_STATE.LOADED,
        response
      });
    }
  }

  /** Wrapped API calls */

  async getWorkflowCoreStatistics() {
    this.cache.set(LOAD_CORE_STATS, {
      params: {},
      apiCall: fetchWorkflowCoreStatistics
    });
    this._pubLoadingStates(LOAD_CORE_STATS, () =>
      fetchWorkflowCoreStatistics()
    );
  }

  async getWorkflowInstances(params) {
    const cachedParams = this._updateRequestCache(
      LOAD_LIST_INSTANCES,
      fetchWorkflowInstances,
      params
    );
    this._pubLoadingStates(LOAD_LIST_INSTANCES, () =>
      fetchWorkflowInstances(cachedParams)
    );
  }

  async getWorkflowInstancesStatistics(params) {
    const cachedParams = this._updateRequestCache(
      LOAD_STATE_STATISTICS,
      fetchWorkflowInstancesStatistics,
      params
    );
    this._pubLoadingStates(LOAD_STATE_STATISTICS, () =>
      fetchWorkflowInstancesStatistics(cachedParams)
    );
  }

  async getWorkflowInstancesBySelection(params, topic, selectionId) {
    const cachedParams = this._updateRequestCache(
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

  async getWorkflowXML(params) {
    const fetchDiagramModel = async params => {
      const xml = await fetchWorkflowXML(params);
      return await parseDiagramXML(xml);
    };

    const cachedParams = this._updateRequestCache(
      LOAD_STATE_DEFINITIONS,
      fetchDiagramModel,
      params
    );

    this._pubLoadingStates(LOAD_STATE_DEFINITIONS, () =>
      fetchDiagramModel(cachedParams)
    );
  }

  async getWorkflowInstancesByIds(params, topic) {
    const cachedParams = this._updateRequestCache(
      topic,
      fetchWorkflowInstancesByIds,
      params
    );

    this._pubLoadingStates(topic, () =>
      fetchWorkflowInstancesByIds(cachedParams)
    );
  }

  /** Update Data */

  // Retruns the names of all api requests which have been executed before, so they can be called again.
  getCachedRequestNames() {
    return this.cache.getEndpointNames().reduce((acc, name) => {
      return {...acc, [name]: name};
    }, {});
  }

  // fetches the data again for all passed endpoints and publishes loading states and result to passed topic
  async update({endpoints, topic, staticData}) {
    this.publisher.publish(topic, {
      state: LOADING_STATE.LOADING
    });

    Promise.all([
      ...endpoints.map(endpointName => {
        const cachedEndpoints = this.cache.getEndpointsbyNames([endpointName]);
        const {params, apiCall} = cachedEndpoints[endpointName];
        return apiCall(params);
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
