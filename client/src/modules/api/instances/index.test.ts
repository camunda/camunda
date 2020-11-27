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
  fetchWorkflowInstancesByIds,
  fetchSequenceFlows,
} from './index';

const successResponse = {
  json: mockResolvedAsyncFn({}),
};

describe('instances api', () => {
  // @ts-expect-error ts-migrate(2540) FIXME: Cannot assign to 'get' because it is a read-only p... Remove this comment to see the full error message
  wrappers.get = mockResolvedAsyncFn(successResponse);
  // @ts-expect-error ts-migrate(2540) FIXME: Cannot assign to 'post' because it is a read-only ... Remove this comment to see the full error message
  wrappers.post = mockResolvedAsyncFn(successResponse);

  beforeEach(() => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockClear' does not exist on type '(url:... Remove this comment to see the full error message
    wrappers.get.mockClear();
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockClear' does not exist on type '(url:... Remove this comment to see the full error message
    wrappers.post.mockClear();
  });

  describe('fetchWorkflowInstance', () => {
    it('should call get with the right url', async () => {
      // given
      const id = 1;
      // when
      await fetchWorkflowInstance(id);
      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.get.mock.calls[0][0]).toBe('/api/workflow-instances/1');
    });
  });

  describe('fetchWorkflowInstances', () => {
    it('should call post with the right url and the right payload', async () => {
      // given
      const options = {
        firstResult: 0,
        maxResults: 0,
        payload: {
          queries: [{}],
        },
      };

      // when
      await fetchWorkflowInstances(options);
      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances?firstResult=0&maxResults=0'
      );
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][1]).toEqual({
        payload: {
          queries: [{}],
        },
      });
    });
  });

  describe('fetchGroupedWorkflows', () => {
    it('should call get the right url', async () => {
      //when
      await fetchGroupedWorkflows();

      //then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.get.mock.calls[0][0]).toBe('/api/workflows/grouped');
    });
  });

  describe('fetchWorkflowCoreStatistics', () => {
    it('should call get with the right url', async () => {
      // when
      await fetchWorkflowCoreStatistics();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.get.mock.calls[0][0]).toBe(
        '/api/workflow-instances/core-statistics'
      );
    });
  });

  describe('fetchWorkflowInstancesByIds', () => {
    it('should call post with the right url and payload', async () => {
      // given
      const ids = ['1', '2', '3'];

      // when
      await fetchWorkflowInstancesByIds(ids);
      // then

      expect(wrappers.post).toHaveBeenCalledWith(
        `/api/workflow-instances?firstResult=0&maxResults=${ids.length}`,
        {
          ids,
          running: true,
          active: true,
          canceled: true,
          completed: true,
          finished: true,
          incidents: true,
        }
      );
    });
  });

  describe('fetchWorkflowInstancesStatistics', () => {
    it('should call post with the right url and payload', async () => {
      //given
      const payload = {
        queries: [{}],
      };

      // when
      await fetchWorkflowInstancesStatistics(payload);

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances/statistics'
      );
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][1]).toEqual({
        queries: [...payload.queries],
      });
      expect(successResponse.json).toBeCalled();
    });
  });

  describe('applyBatchOperation', () => {
    it('should call post with the right payload', async () => {
      // given
      const query = {ids: ['1'], incidents: true, excludeIds: []};

      // when
      await applyBatchOperation(OPERATION_TYPE.RESOLVE_INCIDENT, query);

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances/batch-operation'
      );
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][1].operationType).toBe(
        OPERATION_TYPE.RESOLVE_INCIDENT
      );
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][1].query).toBe(query);
    });
  });

  describe('applyOperation', () => {
    it('should call post with the right payload', async () => {
      // when
      await applyOperation('instance_1', {
        operationType: OPERATION_TYPE.RESOLVE_INCIDENT,
        incidentId: 'incident_1',
      });

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][0]).toBe(
        '/api/workflow-instances/instance_1/operation'
      );
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][1].operationType).toBe(
        OPERATION_TYPE.RESOLVE_INCIDENT
      );
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][1].incidentId).toBe('incident_1');
    });
  });

  describe('fetchSequenceFlows', () => {
    it('should call get with the right url', async () => {
      // given
      const workflowId = ['100'];

      // when
      await fetchSequenceFlows(workflowId);

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.get.mock.calls[0][0]).toBe(
        `/api/workflow-instances/${workflowId}/sequence-flows`
      );
      expect(successResponse.json).toBeCalled();
    });
  });
});
