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
import {MOCK_TOPICS, mockParams, mockApiData} from './core.setup';
jest.mock('modules/utils/bpmn');

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
  let pubLoadingStatesSpy;

  beforeEach(() => {
    dataManager = new DataManager();
    publishSpy = jest.spyOn(dataManager.publisher, 'publish');
    subscribeSpy = jest.spyOn(dataManager.publisher, 'subscribe');
    unsubscribeSpy = jest.spyOn(dataManager.publisher, 'unsubscribe');
    pubLoadingStatesSpy = jest.spyOn(dataManager.publisher, 'pubLoadingStates');
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

  describe('API calls', () => {
    beforeEach(() => {});

    describe('fetch core statistics', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowCoreStatistics(mockParams);

        expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_CORE_STATS
        );
      });
    });
    describe('fetch workflow instances', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstances(mockParams);

        expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_LIST_INSTANCES
        );
      });
    });
    describe('fetch workflow instance statistics', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstancesStatistics(mockParams);

        // then
        expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_STATE_STATISTICS
        );
      });
    });
    describe('fetch workflow XML', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowXML(mockParams);

        // then
        expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(
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
        expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(customTopic);
      });
    });
  });
});
