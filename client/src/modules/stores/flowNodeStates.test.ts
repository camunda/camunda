/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {waitFor} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {flowNodeStatesStore} from './flowNodeStates';

const WORKFLOW_INSTANCE_ID = '2251799813686320';

const mockFlowNodeStatesActive = {
  startEvent1: 'COMPLETED',
  serviceTask1: 'ACTIVE',
};

const mockFlowNodeStatesIncident = {
  startEvent1: 'COMPLETED',
  serviceTask1: 'INCIDENT',
};

const mockFlowNodeStatesCompleted = {
  startEvent1: 'COMPLETED',
  serviceTask1: 'COMPLETED',
};

describe('stores/flowNodeStates', () => {
  beforeEach(async () => {
    // initial fetch
    mockServer.use(
      rest.get(
        `/api/workflow-instances/${WORKFLOW_INSTANCE_ID}/flow-node-states`,
        (_, res, ctx) => res.once(ctx.json(mockFlowNodeStatesActive))
      )
    );

    jest.useFakeTimers();
    flowNodeStatesStore.init(WORKFLOW_INSTANCE_ID);
  });

  afterEach(() => {
    flowNodeStatesStore.reset();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should fetch and store flow node status', async () => {
    await waitFor(() => {
      expect(flowNodeStatesStore.state.flowNodes).toEqual(
        mockFlowNodeStatesActive
      );
    });
  });

  it('should return selectable flow nodes', async () => {
    await waitFor(() => {
      expect(flowNodeStatesStore.selectableFlowNodes).toEqual([
        'startEvent1',
        'serviceTask1',
      ]);
    });
  });

  it('should start polling', async () => {
    mockServer.use(
      // first poll
      rest.get(
        `/api/workflow-instances/${WORKFLOW_INSTANCE_ID}/flow-node-states`,
        (_, res, ctx) => res.once(ctx.json(mockFlowNodeStatesIncident))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(flowNodeStatesStore.state.flowNodes).toEqual(
        mockFlowNodeStatesIncident
      );
    });
  });

  it('should stop polling when all flow nodes are completed', async () => {
    mockServer.use(
      // first poll
      rest.get(
        `/api/workflow-instances/${WORKFLOW_INSTANCE_ID}/flow-node-states`,
        (_, res, ctx) => res.once(ctx.json(mockFlowNodeStatesCompleted))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(flowNodeStatesStore.state.flowNodes).toEqual(
        mockFlowNodeStatesCompleted
      );
    });

    expect(flowNodeStatesStore.intervalId).toBeNull();
  });
});
