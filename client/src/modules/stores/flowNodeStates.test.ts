/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from 'modules/testing-library';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {flowNodeStatesStore} from './flowNodeStates';

const PROCESS_INSTANCE_ID = '2251799813686320';

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
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-states`,
        (_, res, ctx) => res.once(ctx.json(mockFlowNodeStatesActive))
      ),
      rest.get(`/api/process-instances/:id`, (_, res, ctx) =>
        res.once(ctx.json({id: PROCESS_INSTANCE_ID}))
      )
    );

    processInstanceDetailsStore.fetchProcessInstance(PROCESS_INSTANCE_ID);
  });

  afterEach(() => {
    flowNodeStatesStore.reset();
  });

  it('should fetch and store flow node status', async () => {
    flowNodeStatesStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(flowNodeStatesStore.state.flowNodes).toEqual(
        mockFlowNodeStatesActive
      );
    });
  });

  it('should return selectable flow nodes', async () => {
    flowNodeStatesStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(flowNodeStatesStore.selectableFlowNodes).toEqual([
        'startEvent1',
        'serviceTask1',
      ]);
    });
  });

  it('should start polling', async () => {
    jest.useFakeTimers();

    flowNodeStatesStore.init(PROCESS_INSTANCE_ID);

    mockServer.use(
      // first poll
      rest.get(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-states`,
        (_, res, ctx) => res.once(ctx.json(mockFlowNodeStatesIncident))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(flowNodeStatesStore.state.flowNodes).toEqual(
        mockFlowNodeStatesIncident
      );
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should stop polling when all flow nodes are completed', async () => {
    jest.useFakeTimers();

    flowNodeStatesStore.init(PROCESS_INSTANCE_ID);

    mockServer.use(
      // first poll
      rest.get(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-states`,
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

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    flowNodeStatesStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() =>
      expect(flowNodeStatesStore.state.flowNodes).toEqual(
        mockFlowNodeStatesActive
      )
    );

    mockServer.use(
      rest.get(
        `/api/process-instances/${PROCESS_INSTANCE_ID}/flow-node-states`,
        (_, res, ctx) => res.once(ctx.json(mockFlowNodeStatesIncident))
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(flowNodeStatesStore.state.flowNodes).toEqual(
        mockFlowNodeStatesIncident
      )
    );

    window.addEventListener = originalEventListener;
  });
});
