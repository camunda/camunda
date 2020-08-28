/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn} from 'modules/testUtils';
import {SUBSCRIPTION_TOPIC} from 'modules/constants';
import {DataManager} from './index';

import * as instancesApi from 'modules/api/instances';
import * as diagramApi from 'modules/api/diagram';

import {
  mockParams,
  MOCK_TOPICS,
  mockStaticContent,
  customTopic,
} from './index.setup';
jest.mock('modules/utils/bpmn');

// api mocks
instancesApi.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstances = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstanceIncidents = mockResolvedAsyncFn();
instancesApi.fetchSequenceFlows = mockResolvedAsyncFn({});

diagramApi.fetchWorkflowXML = mockResolvedAsyncFn('<xml />');

console.warn = jest.fn();

describe('DataManager', () => {
  let dataManager;
  let subscribeSpy;
  let unsubscribeSpy;
  let pubLoadingStatesSpy;
  let cacheUpdateSpy;
  let fetchAndPublishSpy;

  beforeEach(() => {
    dataManager = new DataManager();
    subscribeSpy = jest.spyOn(dataManager.publisher, 'subscribe');
    unsubscribeSpy = jest.spyOn(dataManager.publisher, 'unsubscribe');
    pubLoadingStatesSpy = jest
      .spyOn(dataManager.publisher, 'pubLoadingStates')
      .mockImplementation((topic, apiCall, params, staticContent) => {
        apiCall();
      });
    cacheUpdateSpy = jest.spyOn(dataManager.cache, 'update');
    fetchAndPublishSpy = jest.spyOn(dataManager, 'fetchAndPublish');

    fetchAndPublishSpy.mockClear();
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

  describe('helper functions', () => {
    it('should fetch & publish', () => {
      const topic = MOCK_TOPICS.FETCH_STATE_FOO;
      const apiCall = jest.fn();

      //when
      dataManager.fetchAndPublish(
        topic,
        apiCall,
        mockParams,
        mockStaticContent
      );

      //then
      expect(cacheUpdateSpy.mock.calls[0]).toEqual([
        topic,
        apiCall,
        mockParams,
      ]);
      expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(topic);
      expect(pubLoadingStatesSpy.mock.calls[0][2]).toEqual(mockStaticContent);
      expect(apiCall).toHaveBeenCalled();
    });
  });

  describe('API calls', () => {
    describe('fetch workflow instances', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstances(mockParams);

        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_LIST_INSTANCES
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          instancesApi.fetchWorkflowInstances
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toEqual(mockParams);
      });
    });
    describe('fetch workflow instance statistics', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstancesStatistics(mockParams);

        // then
        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_STATE_STATISTICS
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          instancesApi.fetchWorkflowInstancesStatistics
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toEqual(mockParams);
      });
    });
    describe('fetch workflow instances by Ids', () => {
      it('should publish loading states to topic', () => {
        // when
        dataManager.getWorkflowInstancesByIds(mockParams, customTopic);

        // then
        expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(customTopic);

        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          instancesApi.fetchWorkflowInstancesByIds
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toEqual(mockParams);
      });
    });
  });
});
