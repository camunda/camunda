/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn} from 'modules/testUtils';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';
import {DataManager, Publisher} from './core';

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

diagramApi.fetchWorkflowXML = mockResolvedAsyncFn('<xml />');

describe('Publisher', () => {
  let publisher;

  beforeEach(() => {
    publisher = new Publisher(MOCK_TOPICS);
  });

  describe('subscribe', () => {
    it('should allow to subscribe to one topcics', () => {
      // given
      const topic = MOCK_TOPICS.FETCH_STATE_FOO;
      const callback = () => {
        /*some callback logic */
      };

      // when
      publisher.subscribe({[topic]: callback});

      // then
      expect(publisher.subscriptions).toEqual({
        [topic]: [callback]
      });
    });

    it('should allow to subscribe to multiple subscriptions', () => {
      // given
      const topicFoo = MOCK_TOPICS.FETCH_STATE_FOO;
      const topicBar = MOCK_TOPICS.FETCH_STATE_BAR;
      const callback = () => {
        /*some callback logic */
      };

      // when
      publisher.subscribe({[topicFoo]: callback, [topicBar]: callback});

      // then
      expect(publisher.subscriptions).toEqual({
        [topicFoo]: [callback],
        [topicBar]: [callback]
      });
    });

    it('should add callbacks to already existing subscriptions', () => {
      // given
      const topicFoo = MOCK_TOPICS.FETCH_STATE_FOO;
      const callbackOne = () => {
        /*some callback logic */
      };
      const callbackTwo = () => {
        /*some callback logic */
      };

      // when
      publisher.subscribe({[topicFoo]: callbackOne});
      publisher.subscribe({[topicFoo]: callbackTwo});

      // then
      expect(publisher.subscriptions).toEqual({
        [topicFoo]: [callbackOne, callbackTwo]
      });
    });

    it('should print an warning if some one subscribes to unknown topic', () => {
      // given
      const topic = 'SOME_UNKNOWN_TOPIC';
      const callback = () => {
        /*some callback logic */
      };
      global.console = {
        warn: jest.fn()
      };

      // when
      publisher.subscribe({[topic]: callback});

      // then
      expect(global.console.warn).toHaveBeenCalled();
    });
  });

  describe('unsubscribe', () => {
    it('should allow to UN-subscribe one or multiple subscriptions', () => {
      // given
      const topicFoo = MOCK_TOPICS.FETCH_STATE_FOO;
      const topicBar = MOCK_TOPICS.FETCH_STATE_BAR;
      const callback = () => {
        /*some callback logic */
      };
      const subscriptions = {[topicFoo]: callback, [topicBar]: callback};
      publisher.subscribe(subscriptions);

      // when
      publisher.unsubscribe(subscriptions);

      // then
      expect(publisher.subscriptions).toEqual({});
    });

    it('should allow to UN-subscribe to a topic others still listen to', () => {
      // given
      const topicFoo = MOCK_TOPICS.FETCH_STATE_FOO;

      const callbackOne = jest.fn();
      const callbackTwo = jest.fn();

      const subscription = {[topicFoo]: callbackOne};
      const otherSubscriptions = {[topicFoo]: callbackTwo};

      publisher.subscribe(subscription);
      publisher.subscribe(otherSubscriptions);

      // when
      publisher.unsubscribe(subscription);

      // then
      expect(publisher.subscriptions).toEqual({[topicFoo]: [callbackTwo]});
    });
  });

  describe('publish', () => {
    it('should publish value for a topic', () => {
      // given
      const topicFoo = MOCK_TOPICS.FETCH_STATE_FOO;
      const publishedValue = 'StringValue';
      const callback = jest.fn();

      // when 1
      publisher.subscribe({[topicFoo]: callback});

      // when 2
      publisher.publish(topicFoo, publishedValue);

      // then
      expect(callback).toHaveBeenCalledWith(publishedValue);
    });

    it('should publish value to all callbacks of a topic', () => {
      // given
      const topicFoo = MOCK_TOPICS.FETCH_STATE_FOO;
      const publishedValue = 'StringValue';
      const callbackOne = jest.fn();
      const callbackTwo = jest.fn();

      // when 1
      publisher.subscribe({[topicFoo]: callbackOne});
      publisher.subscribe({[topicFoo]: callbackTwo});

      // when 2
      publisher.publish(topicFoo, publishedValue);

      // then
      expect(callbackOne).toHaveBeenCalledWith(publishedValue);
      expect(callbackTwo).toHaveBeenCalledWith(publishedValue);
    });

    it('should print an warning if some one publishes to unknown topic', () => {
      // given
      const topic = 'SOME_UNKNOWN_TOPIC';
      const publishedValue = 'StringValue';

      global.console = {
        warn: jest.fn()
      };

      // when 2
      publisher.publish(topic, publishedValue);

      // then
      expect(global.console.warn).toHaveBeenCalled();
    });
  });
});

describe('DataManager', () => {
  let dataManager;
  beforeEach(() => {
    dataManager = new DataManager(MOCK_TOPICS);
  });

  describe('_publishLoadingState', () => {
    it('should publish the LOADING state before the request is made', async () => {
      //given
      const topic = MOCK_TOPICS.FETCH_STATE_FOO;
      const callback = jest.fn();

      dataManager.subscribe({[topic]: callback});
      await dataManager._publishLoadingState(topic, mockApi.success);

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
      await dataManager._publishLoadingState(topic, mockApi.success);

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
      await dataManager._publishLoadingState(topic, mockApi.error);

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
      dataManager._publishLoadingState = jest.fn();
      // dataManager._publishLoadingState.mockClear();
    });

    describe('fetch workflow instances', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstances(mockParams);

        // then
        expect(dataManager._publishLoadingState).toHaveBeenCalledWith(
          SUBSCRIPTION_TOPIC.LOAD_LIST_INSTANCES,
          instancesApi.fetchWorkflowInstances,
          mockParams
        );
      });
    });

    describe('fetch workflow instance statistics', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowInstancesStatistics(mockParams);

        // then
        expect(dataManager._publishLoadingState).toHaveBeenCalledWith(
          SUBSCRIPTION_TOPIC.LOAD_STATE_STATISTICS,
          instancesApi.fetchWorkflowInstancesStatistics,
          mockParams
        );
      });
    });
    describe('fetch workflow XML', () => {
      it('should publish loading stats to topic', () => {
        // when
        dataManager.getWorkflowXML(mockParams);

        // then
        expect(dataManager._publishLoadingState).toHaveBeenCalledWith(
          SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS,
          dataManager.fetchDiagramModel,
          mockParams
        );
      });
    });
  });
});
