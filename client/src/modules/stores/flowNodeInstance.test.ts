/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {
  createInstance,
  createMultiInstanceFlowNodeInstances,
} from 'modules/testUtils';
import {flowNodeInstanceStore} from './flowNodeInstance';
import {modificationsStore} from './modifications';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

const PROCESS_INSTANCE_ID = 'processInstance';
const mockFlowNodeInstances =
  createMultiInstanceFlowNodeInstances(PROCESS_INSTANCE_ID);

describe('stores/flowNodeInstance', () => {
  afterEach(() => {
    processInstanceDetailsStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should initialize, add and remove nested sub trees from store', async () => {
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    processInstanceDetailsStore.setProcessInstance(
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

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level2);

    await flowNodeInstanceStore.fetchSubTree({
      treePath: `${PROCESS_INSTANCE_ID}/2251799813686156`,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      ...mockFlowNodeInstances.level1,
      ...mockFlowNodeInstances.level2,
    });

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level3);

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
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    processInstanceDetailsStore.setProcessInstance(
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

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1Next);

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
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    processInstanceDetailsStore.setProcessInstance(
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

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1Prev);

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
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStore.setProcessInstance(
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

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level2);

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
      mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

      pollInstancesSpy = jest.spyOn(flowNodeInstanceStore, 'pollInstances');
      stopPollingSpy = jest.spyOn(flowNodeInstanceStore, 'stopPolling');

      jest.useFakeTimers();
    });

    afterEach(() => {
      pollInstancesSpy.mockReset();
      stopPollingSpy.mockReset();
      modificationsStore.reset();
      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should start polling when process instance is active', async () => {
      processInstanceDetailsStore.setProcessInstance(
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
      mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

      jest.runOnlyPendingTimers();

      await waitFor(() => {
        expect(pollInstancesSpy).toHaveBeenCalled();
      });
    });

    it('should not start polling when process instance is completed', async () => {
      processInstanceDetailsStore.setProcessInstance(
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
      mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

      jest.runOnlyPendingTimers();
      expect(pollInstancesSpy).not.toHaveBeenCalled();
    });

    it('should stop polling after process instance has finished', async () => {
      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: PROCESS_INSTANCE_ID,
          state: 'ACTIVE',
        })
      );

      flowNodeInstanceStore.init();

      await waitFor(() => {
        expect(flowNodeInstanceStore.state.status).toBe('fetched');
      });

      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: PROCESS_INSTANCE_ID,
          state: 'COMPLETED',
        })
      );

      jest.runOnlyPendingTimers();
      expect(stopPollingSpy).toHaveBeenCalled();
      expect(pollInstancesSpy).not.toHaveBeenCalled();
    });

    it('should not start polling after flow node instances are fetched during modification mode', async () => {
      modificationsStore.enableModificationMode();
      mockFetchFlowNodeInstances().withServerError();

      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: PROCESS_INSTANCE_ID,
          state: 'ACTIVE',
        })
      );

      flowNodeInstanceStore.fetchFlowNodeInstances({
        treePath: 'test',
      });

      await waitFor(() =>
        expect(flowNodeInstanceStore.state.status).toBe('error')
      );
      jest.runOnlyPendingTimers();

      mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

      expect(pollInstancesSpy).not.toHaveBeenCalled();
    });
  });
});
