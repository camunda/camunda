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

import {fetchEvents} from 'modules/api/events';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';

import {
  mockParams,
  mockScopeId,
  MOCK_TOPICS,
  mockStaticContent,
  customTopic,
  mockWorkflowInstance
} from './core.setup';
jest.mock('modules/utils/bpmn');

// api mocks
instancesApi.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstances = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstanceIncidents = mockResolvedAsyncFn();
instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn({});

diagramApi.fetchWorkflowXML = mockResolvedAsyncFn('<xml />');

console.warn = jest.fn();

describe('DataManager', () => {
  let dataManager;
  let publishSpy;
  let subscribeSpy;
  let unsubscribeSpy;
  let pubLoadingStatesSpy;
  let cacheUpdateSpy;
  let fetchAndPublishSpy;

  beforeEach(() => {
    dataManager = new DataManager();
    publishSpy = jest.spyOn(dataManager.publisher, 'publish');
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
        mockParams
      ]);
      expect(pubLoadingStatesSpy.mock.calls[0][0]).toBe(topic);
      expect(pubLoadingStatesSpy.mock.calls[0][2]).toEqual(mockStaticContent);
      expect(apiCall).toHaveBeenCalled();
    });
  });

  describe('API calls', () => {
    describe('fetch core statistics', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowCoreStatistics(mockParams);

        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_CORE_STATS
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          instancesApi.fetchWorkflowCoreStatistics
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toEqual(mockParams);
      });
    });
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

    describe('fetch workflow instance', () => {
      it('should fetch and publish', () => {
        dataManager.getWorkflowInstance(mockWorkflowInstance.id);

        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_INSTANCE
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          instancesApi.fetchWorkflowInstance
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toBe(
          mockWorkflowInstance.id
        );
      });
    });

    describe('fetch Incidents', () => {
      it('should fetch and publish', () => {
        dataManager.getIncidents(mockWorkflowInstance);

        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_INCIDENTS
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          instancesApi.fetchWorkflowInstanceIncidents
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toBe(mockWorkflowInstance);
      });
    });

    describe('fetch Events', () => {
      it('should fetch and publish', () => {
        dataManager.getEvents(mockWorkflowInstance.id);

        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_EVENTS
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(fetchEvents);
        expect(fetchAndPublishSpy.mock.calls[0][2]).toBe(
          mockWorkflowInstance.id
        );
      });
    });

    describe('fetch Activity Instance Tree', () => {
      it('should fetch and publish', () => {
        dataManager.getActivityInstancesTreeData(mockWorkflowInstance);

        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_INSTANCE_TREE
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          fetchActivityInstancesTree
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toBe(
          mockWorkflowInstance.id
        );
      });
    });

    describe('fetch variables', () => {
      it('should fetch and publish', () => {
        dataManager.getVariables(mockWorkflowInstance.id, mockScopeId);

        expect(fetchAndPublishSpy.mock.calls[0][0]).toBe(
          SUBSCRIPTION_TOPIC.LOAD_VARIABLES
        );
        expect(fetchAndPublishSpy.mock.calls[0][1]).toBe(
          instancesApi.fetchVariables
        );
        expect(fetchAndPublishSpy.mock.calls[0][2]).toEqual({
          instanceId: mockWorkflowInstance.id,
          scopeId: mockScopeId
        });
      });
    });
  });
});
