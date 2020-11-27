/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {flowNodeInstanceStore} from './flowNodeInstance';
import {currentInstanceStore} from './currentInstance';
import {createInstance} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';

const currentInstanceMock = createInstance();

const activityInstancesMock = {
  children: [
    {
      id: '2251799813685475',
      type: 'START_EVENT',
      state: 'COMPLETED',
      activityId: 'start',
      startDate: '2020-10-06T11:11:13.496+0000',
      endDate: '2020-10-06T11:11:13.605+0000',
      parentId: '2251799813685471',
      children: [],
    },
    {
      id: '2251799813685486',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      activityId: 'neverFails',
      startDate: '2020-10-06T11:11:13.717+0000',
      endDate: null,
      parentId: '2251799813685471',
      children: [],
    },
  ],
};

describe('stores/flowNodeInstance', () => {
  afterEach(() => {
    flowNodeInstanceStore.reset();
    currentInstanceStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(activityInstancesMock))
      )
    );
  });

  it('should set current selection and fetch instance execution history when current instance is available', async () => {
    currentInstanceStore.setCurrentInstance({id: 123, state: 'CANCELED'});
    flowNodeInstanceStore.init();

    expect(flowNodeInstanceStore.state.selection).toEqual({
      flowNodeId: null,
      treeRowIds: [123],
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.response).toEqual(
        activityInstancesMock
      )
    );

    expect(flowNodeInstanceStore.state.isInitialLoadComplete).toBe(true);
  });

  it('should poll if current instance is running', async () => {
    jest.useFakeTimers();
    currentInstanceStore.setCurrentInstance(currentInstanceMock);
    flowNodeInstanceStore.init();

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.response).toEqual(
        activityInstancesMock
      )
    );

    const secondActivityInstancesMock = {
      children: [
        {
          id: '111',
          type: 'START_EVENT',
          state: 'COMPLETED',
          activityId: 'start',
          startDate: '2020-10-06T11:11:13.496+0000',
          endDate: '2020-10-06T11:11:13.605+0000',
          parentId: '2251799813685471',
          children: [],
        },
        {
          id: '222',
          type: 'SERVICE_TASK',
          state: 'ACTIVE',
          activityId: 'neverFails',
          startDate: '2020-10-06T11:11:13.717+0000',
          endDate: null,
          parentId: '2251799813685471',
          children: [],
        },
      ],
    };

    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(secondActivityInstancesMock))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.response).toEqual(
        secondActivityInstancesMock
      );
    });

    currentInstanceStore.setCurrentInstance(
      createInstance({state: 'CANCELED'})
    );

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.response).toEqual(
        secondActivityInstancesMock
      );
    });

    jest.useRealTimers();
  });

  it('should set current selection', async () => {
    expect(flowNodeInstanceStore.state.selection).toEqual({
      treeRowIds: [],
      flowNodeId: null,
    });

    flowNodeInstanceStore.setCurrentSelection({
      treeRowIds: [1, 2],
      flowNodeId: 2,
    });
    expect(flowNodeInstanceStore.state.selection).toEqual({
      treeRowIds: [1, 2],
      flowNodeId: 2,
    });
  });

  it('should reset store', async () => {
    currentInstanceStore.setCurrentInstance({id: 123, state: 'CANCELED'});
    flowNodeInstanceStore.init();

    expect(flowNodeInstanceStore.state.selection).toEqual({
      flowNodeId: null,
      treeRowIds: [123],
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.response).toEqual(
        activityInstancesMock
      )
    );
    expect(flowNodeInstanceStore.state.isInitialLoadComplete).toBe(true);

    flowNodeInstanceStore.reset();
    expect(flowNodeInstanceStore.state.selection).toEqual({
      treeRowIds: [],
      flowNodeId: null,
    });
    expect(flowNodeInstanceStore.state.response).toEqual(null);
    expect(flowNodeInstanceStore.state.isInitialLoadComplete).toEqual(false);
  });

  it('should handle request failure', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
      )
    );
    currentInstanceStore.setCurrentInstance({id: 123, state: 'CANCELED'});
    flowNodeInstanceStore.init();

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.isFailed).toBe(true)
    );
  });

  it('should change current selection', async () => {
    currentInstanceStore.setCurrentInstance({id: 111, state: 'CANCELED'});
    flowNodeInstanceStore.setCurrentSelection({
      flowNodeId: 222,
      treeRowIds: [222],
    });

    // select root node if we try to set current selection to a node that is already selected
    flowNodeInstanceStore.changeCurrentSelection({
      id: 222,
      activityId: 'nodeActivityId2',
    });

    expect(flowNodeInstanceStore.state.selection).toEqual({
      flowNodeId: null,
      treeRowIds: [111],
    });

    // set current selection to something other than root node
    flowNodeInstanceStore.changeCurrentSelection({
      id: 333,
      activityId: 'nodeActivityId3',
    });
    expect(flowNodeInstanceStore.state.selection).toEqual({
      flowNodeId: 'nodeActivityId3',
      treeRowIds: [333],
    });

    // set current selection to root node again
    flowNodeInstanceStore.changeCurrentSelection({
      id: 111,
      activityId: 'nodeActivityId1',
    });
    expect(flowNodeInstanceStore.state.selection).toEqual({
      flowNodeId: null,
      treeRowIds: [111],
    });
  });

  it('should get instance execution history availability', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
      )
    );
    currentInstanceStore.setCurrentInstance({id: 123, state: 'ACTIVE'});
    jest.useFakeTimers();
    flowNodeInstanceStore.init();

    expect(flowNodeInstanceStore.isInstanceExecutionHistoryAvailable).toBe(
      false
    );
    await waitFor(() =>
      expect(flowNodeInstanceStore.state.isFailed).toBe(true)
    );
    expect(flowNodeInstanceStore.isInstanceExecutionHistoryAvailable).toBe(
      false
    );

    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(activityInstancesMock))
      )
    );

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(flowNodeInstanceStore.state.response).toEqual(
        activityInstancesMock
      )
    );

    expect(flowNodeInstanceStore.isInstanceExecutionHistoryAvailable).toBe(
      true
    );

    jest.useRealTimers();
  });

  it('should get instance execution history', async () => {
    currentInstanceStore.setCurrentInstance({id: 123, state: 'ACTIVE'});
    jest.useFakeTimers();
    flowNodeInstanceStore.init();

    expect(flowNodeInstanceStore.instanceExecutionHistory).toEqual(null);
    await waitFor(() =>
      expect(flowNodeInstanceStore.state.response).toEqual(
        activityInstancesMock
      )
    );

    expect(flowNodeInstanceStore.instanceExecutionHistory).toEqual({
      ...activityInstancesMock,
      id: 123,
      type: 'WORKFLOW',
      state: 'ACTIVE',
    });
    currentInstanceStore.setCurrentInstance(null);
    expect(flowNodeInstanceStore.instanceExecutionHistory).toEqual(null);

    jest.useRealTimers();
  });

  it('should get mapped flow nodes', async () => {
    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
      )
    );
    currentInstanceStore.setCurrentInstance({id: 123, state: 'ACTIVE'});
    jest.useFakeTimers();
    flowNodeInstanceStore.init();

    expect(flowNodeInstanceStore.flowNodeIdToFlowNodeInstanceMap).toEqual(
      new Map()
    );

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.isFailed).toBe(true)
    );
    expect(flowNodeInstanceStore.flowNodeIdToFlowNodeInstanceMap).toEqual(
      new Map()
    );

    mockServer.use(
      rest.post('/api/activity-instances', (_, res, ctx) =>
        res.once(ctx.json(activityInstancesMock))
      )
    );
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(flowNodeInstanceStore.state.response).toEqual(
        activityInstancesMock
      )
    );

    expect(flowNodeInstanceStore.flowNodeIdToFlowNodeInstanceMap).not.toEqual(
      new Map()
    );
    expect(
      flowNodeInstanceStore.flowNodeIdToFlowNodeInstanceMap.has('start')
    ).toBe(true);
    expect(
      flowNodeInstanceStore.flowNodeIdToFlowNodeInstanceMap.has('neverFails')
    ).toBe(true);

    jest.useRealTimers();
  });

  it('should start loading', async () => {
    expect(flowNodeInstanceStore.state.isLoading).toBe(false);
    flowNodeInstanceStore.startLoading();
    expect(flowNodeInstanceStore.state.isLoading).toBe(true);
  });

  it('should complete initial load', async () => {
    expect(flowNodeInstanceStore.state.isInitialLoadComplete).toBe(false);
    flowNodeInstanceStore.completeInitialLoad();
    expect(flowNodeInstanceStore.state.isInitialLoadComplete).toBe(true);
  });

  it('should get areMultipleNodesSelected', async () => {
    expect(flowNodeInstanceStore.areMultipleNodesSelected).toBe(false);

    flowNodeInstanceStore.setCurrentSelection({
      treeRowIds: [1],
      flowNodeId: 1,
    });
    expect(flowNodeInstanceStore.areMultipleNodesSelected).toBe(false);

    flowNodeInstanceStore.setCurrentSelection({
      treeRowIds: [1, 2],
      flowNodeId: 1,
    });
    expect(flowNodeInstanceStore.areMultipleNodesSelected).toBe(true);
  });
});
