/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn} from 'modules/testUtils';
import * as wrappers from 'modules/request/wrappers';
import {OPERATION_TYPE} from 'modules/constants';

import {
  fetchWorkflowCoreStatistics,
  applyOperation,
  applyBatchOperation,
  fetchWorkflowInstance,
  fetchWorkflowInstances,
  fetchGroupedWorkflows,
  fetchWorkflowInstancesStatistics,
  fetchWorkflowInstancesByIds
} from './instances';

const successResponse = {
  json: mockResolvedAsyncFn({})
};

describe('instances api', () => {
  wrappers.get = mockResolvedAsyncFn(successResponse);
  wrappers.post = mockResolvedAsyncFn(successResponse);

  beforeEach(() => {
    wrappers.get.mockClear();
    wrappers.post.mockClear();
  });

  describe('fetchWorkflowInstance', () => {
    it('should call get with the right url', async () => {
      // given
      const id = 1;
      // when
      await fetchWorkflowInstance(id);
      // then
      expect(wrappers.get.mock.calls[0][0]).toBe('/api/workflow-instances/1');
      expect(successResponse.json).toBeCalled();
    });
  });

  describe('fetchWorkflowInstances', () => {
    it('should call post with the right url and the right payload', async () => {
      // given
      const options = {
        firstResult: 0,
        maxResults: 0,
        payload: {
          queries: [{}]
        }
      };

      // when
      await fetchWorkflowInstances(options);
      // then
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances?firstResult=0&maxResults=0'
      );
      expect(wrappers.post.mock.calls[0][1]).toEqual({
        payload: {
          queries: [{}]
        }
      });
      expect(successResponse.json).toBeCalled();
    });
  });

  describe('fetchGroupedWorkflows', () => {
    it('should call get the right url', async () => {
      //when
      await fetchGroupedWorkflows();

      //then
      expect(wrappers.get.mock.calls[0][0]).toBe('/api/workflows/grouped');
      expect(successResponse.json).toBeCalled();
    });
  });

  describe('fetchWorkflowCoreStatistics', () => {
    it('should call get with the right url', async () => {
      // when
      await fetchWorkflowCoreStatistics();

      // then
      expect(wrappers.get.mock.calls[0][0]).toBe(
        '/api/workflow-instances/core-statistics'
      );
      expect(successResponse.json).toBeCalled();
    });
  });

  describe('fetchWorkflowInstancesByIds', () => {
    it('should call post with the right url and payload', async () => {
      // given
      const ids = ['1', '2', '3'];

      // when
      await fetchWorkflowInstancesByIds(ids);
      // then

      expect(wrappers.post.mock.calls[0][0]).toBe(
        `/api/workflow-instances?firstResult=0&maxResults=${ids.length}`
      );
      expect(wrappers.post.mock.calls[0][1]).toEqual({
        queries: [
          {
            ids,
            running: true,
            active: true,
            canceled: true,
            completed: true,
            finished: true,
            incidents: true
          }
        ]
      });
    });
  });

  describe('fetchWorkflowInstancesStatistics', () => {
    it('should call post with the right url and payload', async () => {
      //given
      const payload = {
        queries: [{}]
      };

      // when
      await fetchWorkflowInstancesStatistics(payload);

      // then
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances/statistics'
      );
      expect(wrappers.post.mock.calls[0][1]).toEqual({
        queries: [...payload.queries]
      });
      expect(successResponse.json).toBeCalled();
    });
  });

  describe('applyBatchOperation', () => {
    it('should call post with the right payload', async () => {
      // given
      const queries = [{id: 1, incidents: true}];

      // when
      await applyBatchOperation(OPERATION_TYPE.RESOLVE_INCIDENT, queries);

      // then
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances/operation'
      );
      expect(wrappers.post.mock.calls[0][1].operationType).toBe(
        OPERATION_TYPE.RESOLVE_INCIDENT
      );
      expect(wrappers.post.mock.calls[0][1].queries).toBe(queries);
    });
  });

  describe('applyOperation', () => {
    it('should call post with the right payload', async () => {
      // when
      await applyOperation('instance_1', {
        operationType: OPERATION_TYPE.RESOLVE_INCIDENT,
        incidentId: 'incident_1'
      });

      // then
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances/instance_1/operation'
      );
      expect(wrappers.post.mock.calls[0][1].operationType).toBe(
        OPERATION_TYPE.RESOLVE_INCIDENT
      );
      expect(wrappers.post.mock.calls[0][1].incidentId).toBe('incident_1');
    });
  });
});
