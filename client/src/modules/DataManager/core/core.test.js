/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn} from 'modules/testUtils';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';
import {DataManager} from './core';

import * as instancesApi from 'modules/api/instances/instances';
import * as diagramApi from 'modules/api/diagram/diagram';

jest.mock('modules/utils/bpmn');

const MOCK_TOPICS = {
  FETCH_STATE_FOO: 'FETCH_STATE_FOO',
  FETCH_STATE_BAR: 'FETCH_STATE_BAR'
};

const mockParams = {};

const mockApiData = {
  success: {data: ['someData', 'someMoreData'], error: null},
  error: {data: [], error: 'fetchError'}
};

const mockApi = {
  success: jest.fn(() => Promise.resolve(mockApiData.success)),
  error: jest.fn(() => Promise.resolve(mockApiData.error))
};

// api mocks
instancesApi.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstances = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn();
diagramApi.fetchWorkflowXML = mockResolvedAsyncFn('<xml />');

console.warn = jest.fn();

describe('DataManager', () => {
  let dataManager;
  let publishSpy;
  let subscribeSpy;
  let unsubscribeSpy;
  let cacheSetSpy;
  let cachegetEndpointsbyNamesSpy;
  let cacheGetEndpointNames;

  beforeEach(() => {
    dataManager = new DataManager();
    publishSpy = jest.spyOn(dataManager.publisher, 'publish');
    subscribeSpy = jest.spyOn(dataManager.publisher, 'subscribe');
    unsubscribeSpy = jest.spyOn(dataManager.publisher, 'unsubscribe');
    cacheSetSpy = jest.spyOn(dataManager.cache, 'set');
    cachegetEndpointsbyNamesSpy = jest.spyOn(
      dataManager.cache,
      'getEndpointsbyNames'
    );
    cacheGetEndpointNames = jest.spyOn(dataManager.cache, 'getEndpointNames');
  });

  describe('Publisher interface', () => {
    it('should pass on requests to the publisher', () => {
      const subscriptions = {};
      dataManager.subscribe(subscriptions);
      expect(subscribeSpy.mock.calls[0][0]).toEqual(subscriptions);

      dataManager.unsubscribe(subscriptions);
      expect(unsubscribeSpy.mock.calls[0][0]).toEqual(subscriptions);
    });
  });

  describe('cache request parameter', () => {
    it('should set params in cache and pass through', () => {
      const name = '';
      const params = {};
      const apiCall = '';
      const cachedParams = dataManager._updateRequestCache(
        name,
        apiCall,
        params
      );

      expect(cacheSetSpy.mock.calls[0][0]).toEqual(name, {params, apiCall});
      expect(cachedParams).toEqual(params);
    });

    it('should retrieve params from cache', () => {
      //given
      const name = 'NAME';
      const apiCall = 'apicallFunctionName';

      dataManager._updateRequestCache(name, apiCall);
      expect(cachegetEndpointsbyNamesSpy.mock.calls[0][0]).toEqual([name]);
    });

    it('should return all cached endpoint names', () => {
      //when
      dataManager.getCachedRequestNames();

      //then
      expect(cacheGetEndpointNames).toHaveBeenCalled();
    });
  });

  describe('publish loading states', () => {
    it('should publish the LOADING state before the request is made', async () => {
      //given
      const topic = MOCK_TOPICS.FETCH_STATE_FOO;
      const callback = jest.fn();

      dataManager.subscribe({[topic]: callback});
      await dataManager._pubLoadingStates(topic, mockApi.success);

      expect(callback.mock.calls[0][0]).toEqual({
        state: LOADING_STATE.LOADING
      });
    });

    it('should publish the LOADED state after a successful request', async () => {
      //given
      const topic = MOCK_TOPICS.FETCH_STATE_FOO;
      const callback = jest.fn();

      // when
      dataManager.subscribe({[topic]: callback});
      await dataManager._pubLoadingStates(topic, mockApi.success);

      // then
      expect(callback.mock.calls).toEqual([
        [{state: LOADING_STATE.LOADING}],
        [
          {
            state: LOADING_STATE.LOADED,
            response: mockApiData.success
          }
        ]
      ]);
    });

    it('should publish the ERROR state after a unsuccessful request', async () => {
      //given
      const topic = MOCK_TOPICS.FETCH_STATE_FOO;
      const callback = jest.fn();

      // when
      dataManager.subscribe({[topic]: callback});
      await dataManager._pubLoadingStates(topic, mockApi.error);

      // then
      expect(callback.mock.calls).toEqual([
        [{state: LOADING_STATE.LOADING}],
        [
          {
            state: LOADING_STATE.LOAD_FAILED,
            response: mockApiData.error
          }
        ]
      ]);
    });
  });

  describe('API calls', () => {
    beforeEach(() => {
      dataManager._pubLoadingStates = jest.fn();
      dataManager._pubLoadingStates.mockClear();
    });

    describe('fetch core statistics', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowCoreStatistics(mockParams);

        expect(dataManager._pubLoadingStates.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_CORE_STATS
        );
      });
    });
    describe('fetch workflow instances', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstances(mockParams);

        expect(dataManager._pubLoadingStates.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_LIST_INSTANCES
        );
      });
    });
    describe('fetch workflow instance statistics', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstancesStatistics(mockParams);

        // then
        expect(dataManager._pubLoadingStates.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_STATE_STATISTICS
        );
      });
    });
    describe('fetch workflow XML', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowXML(mockParams);

        // then
        expect(dataManager._pubLoadingStates.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS
        );
      });
    });
    describe('fetch workflow instances by selection', () => {
      it('should publish loading states to topic', () => {
        //given
        const customTopic = 'CUSTOM_TOPIC';

        // when
        dataManager.getWorkflowInstancesBySelection(mockParams, customTopic);

        // then
        expect(publishSpy).toHaveBeenCalledWith(customTopic, {
          state: LOADING_STATE.LOADING
        });
      });
    });

    describe('fetch workflow instances by Ids', () => {
      it('should publish loading states to topic', () => {
        //given
        const customTopic = 'CUSTOM_TOPIC';

        // when
        dataManager.getWorkflowInstancesByIds(mockParams, customTopic);

        // then
        expect(dataManager._pubLoadingStates.mock.calls[0][0]).toBe(
          customTopic
        );
      });
    });
  });
});
