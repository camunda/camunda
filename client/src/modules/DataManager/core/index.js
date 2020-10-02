/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {fetchWorkflowInstancesByIds} from 'modules/api/instances';

import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

import RequestCache from '../cache';
import Publisher from '../publisher';
import Poll from '../poll';

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

  fetchAndPublish = (topic, apiCall, params, staticContent) => {
    const cachedParams = this.cache.update(topic, apiCall, params);
    this.publisher.pubLoadingStates(
      topic,
      () => apiCall(cachedParams),
      staticContent
    );
  };

  getWorkflowInstancesByIds = (params, topic) => {
    this.fetchAndPublish(topic, fetchWorkflowInstancesByIds, params);
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
