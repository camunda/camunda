/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {
  createInstance,
  createMultiInstanceFlowNodeInstances,
} from 'modules/testUtils';
import {flowNodeInstanceStore} from './flowNodeInstance';

const PROCESS_INSTANCE_ID = 'processInstance';
const mockFlowNodeInstances =
  createMultiInstanceFlowNodeInstances(PROCESS_INSTANCE_ID);

describe('stores/flowNodeInstance', () => {
  afterEach(() => {
    currentInstanceStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should initialize, add and remove nested sub trees from store', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level1))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level2))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level3))
      )
    );

    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
      mockFlowNodeInstances.level1
    );

    await flowNodeInstanceStore.fetchSubTree({
      treePath: `${PROCESS_INSTANCE_ID}/2251799813686156`,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      ...mockFlowNodeInstances.level1,
      ...mockFlowNodeInstances.level2,
    });

    await flowNodeInstanceStore.fetchSubTree({
      treePath: `${PROCESS_INSTANCE_ID}/2251799813686156/2251799813686166`,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      ...mockFlowNodeInstances.level1,
      ...mockFlowNodeInstances.level2,
      ...mockFlowNodeInstances.level3,
    });

    await flowNodeInstanceStore.removeSubTree({
      treePath: `${PROCESS_INSTANCE_ID}/2251799813686156`,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
      mockFlowNodeInstances.level1
    );
  });

  it('should fetch next instances', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level1))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level1Next))
      )
    );

    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeInstanceStore.init();
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
        mockFlowNodeInstances.level1
      )
    );

    await flowNodeInstanceStore.fetchNext(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
        [PROCESS_INSTANCE_ID]: {
          ...mockFlowNodeInstances.level1[PROCESS_INSTANCE_ID],
          children: [
            ...mockFlowNodeInstances.level1[PROCESS_INSTANCE_ID]!.children,
            ...mockFlowNodeInstances.level1Next[PROCESS_INSTANCE_ID]!.children,
          ],
        },
      });
    });
  });

  it('should fetch previous instances', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level1))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level1Prev))
      )
    );

    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeInstanceStore.init();
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
        mockFlowNodeInstances.level1
      )
    );

    await flowNodeInstanceStore.fetchPrevious(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
        [PROCESS_INSTANCE_ID]: {
          ...mockFlowNodeInstances.level1[PROCESS_INSTANCE_ID],
          children: [
            ...mockFlowNodeInstances.level1Prev[PROCESS_INSTANCE_ID]!.children,
            ...mockFlowNodeInstances.level1[PROCESS_INSTANCE_ID]!.children,
          ],
        },
      });
    });
  });

  it('should retry fetch on network reconnection', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level1))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level2))
      )
    );

    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    flowNodeInstanceStore.init();

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
        mockFlowNodeInstances.level1
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
        ...mockFlowNodeInstances.level1,
        ...mockFlowNodeInstances.level2,
      })
    );

    window.addEventListener = originalEventListener;
  });

  describe('polling', () => {
    let pollInstancesSpy: jest.SpyInstance;
    let stopPollingSpy: jest.SpyInstance;

    beforeEach(() => {
      mockServer.use(
        rest.post('/api/flow-node-instances', (_, res, ctx) =>
          res.once(ctx.json(mockFlowNodeInstances.level1))
        )
      );

      pollInstancesSpy = jest.spyOn(flowNodeInstanceStore, 'pollInstances');
      stopPollingSpy = jest.spyOn(flowNodeInstanceStore, 'stopPolling');

      jest.useFakeTimers();
    });

    afterEach(() => {
      pollInstancesSpy.mockReset();
      stopPollingSpy.mockReset();
      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should start polling when process instance is active', async () => {
      currentInstanceStore.setCurrentInstance(
        createInstance({
          id: PROCESS_INSTANCE_ID,
          state: 'ACTIVE',
        })
      );

      flowNodeInstanceStore.init();

      await waitFor(() => {
        expect(flowNodeInstanceStore.state.status).toBe('fetched');
      });

      // polling
      mockServer.use(
        rest.post('/api/flow-node-instances', (_, res, ctx) =>
          res.once(ctx.json(mockFlowNodeInstances.level1))
        )
      );
      jest.runOnlyPendingTimers();

      await waitFor(() => {
        expect(pollInstancesSpy).toHaveBeenCalled();
      });
    });

    it('should not start polling when process instance is completed', async () => {
      currentInstanceStore.setCurrentInstance(
        createInstance({
          id: PROCESS_INSTANCE_ID,
          state: 'COMPLETED',
        })
      );

      flowNodeInstanceStore.init();

      await waitFor(() => {
        expect(flowNodeInstanceStore.state.status).toBe('fetched');
      });

      // polling
      mockServer.use(
        rest.post('/api/flow-node-instances', (_, res, ctx) =>
          res.once(ctx.json(mockFlowNodeInstances.level1))
        )
      );

      jest.runOnlyPendingTimers();
      expect(pollInstancesSpy).not.toHaveBeenCalled();
    });

    it('should stop polling after process instance has finished', async () => {
      currentInstanceStore.setCurrentInstance(
        createInstance({
          id: PROCESS_INSTANCE_ID,
          state: 'ACTIVE',
        })
      );

      flowNodeInstanceStore.init();

      await waitFor(() => {
        expect(flowNodeInstanceStore.state.status).toBe('fetched');
      });

      currentInstanceStore.setCurrentInstance(
        createInstance({
          id: PROCESS_INSTANCE_ID,
          state: 'COMPLETED',
        })
      );

      jest.runOnlyPendingTimers();
      expect(stopPollingSpy).toHaveBeenCalled();
      expect(pollInstancesSpy).not.toHaveBeenCalled();
    });
  });
});
