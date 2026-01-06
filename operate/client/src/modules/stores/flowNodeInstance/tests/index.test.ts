/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {flowNodeInstanceStore} from '../';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFlowNodeInstances, PROCESS_INSTANCE_ID} from './mocks';

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
      }),
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
      mockFlowNodeInstances.level1,
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
      mockFlowNodeInstances.level1,
    );
  });

  it('should fetch next instances', async () => {
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    flowNodeInstanceStore.init();
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
        mockFlowNodeInstances.level1,
      ),
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
      }),
    );

    flowNodeInstanceStore.init();
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
        mockFlowNodeInstances.level1,
      ),
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

    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    flowNodeInstanceStore.init();

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
        mockFlowNodeInstances.level1,
      ),
    );

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level2);

    eventListeners.online();

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
        ...mockFlowNodeInstances.level1,
        ...mockFlowNodeInstances.level2,
      }),
    );
  });
});
