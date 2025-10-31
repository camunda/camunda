/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {sequenceFlowsStore} from './sequenceFlows';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {createInstance, createSequenceFlows} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {mockFetchSequenceFlows} from 'modules/mocks/api/processInstances/sequenceFlows';

describe('stores/sequenceFlows', () => {
  const mockSequenceFlows = createSequenceFlows();

  beforeEach(() => {
    mockFetchSequenceFlows().withSuccess(mockSequenceFlows, {
      expectPolling: false,
    });
  });

  afterEach(() => {
    sequenceFlowsStore.reset();
    processInstanceDetailsStore.reset();
  });

  it('should fetch sequence flows when current instance is available', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'}),
    );

    sequenceFlowsStore.init();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
      ]),
    );
  });

  it('should poll when current instance is running', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'ACTIVE'}),
    );

    vi.useFakeTimers({shouldAdvanceTime: true});
    sequenceFlowsStore.init();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
      ]),
    );

    mockFetchSequenceFlows().withSuccess(
      [
        ...mockSequenceFlows,
        {
          processInstanceId: '2251799813693731',
          activityId: 'SequenceFlow_1sz6737',
        },
      ],
      {expectPolling: true},
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
      ]),
    );

    mockFetchSequenceFlows().withSuccess(
      [
        ...mockSequenceFlows,
        {
          processInstanceId: '2251799813693731',
          activityId: 'SequenceFlow_1sz6737',
        },
        {
          processInstanceId: '2251799813693732',
          activityId: 'SequenceFlow_1sz6738',
        },
      ],
      {expectPolling: true},
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
        'SequenceFlow_1sz6738',
      ]),
    );

    // stop polling when current instance is no longer running
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'}),
    );

    vi.runOnlyPendingTimers();
    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
        'SequenceFlow_1sz6738',
      ]),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should reset store', async () => {
    await sequenceFlowsStore.fetchProcessSequenceFlows('1');

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
      ]),
    );

    sequenceFlowsStore.reset();

    expect(sequenceFlowsStore.state.items).toEqual([]);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      }),
    );
    sequenceFlowsStore.init();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
      ]),
    );

    mockFetchSequenceFlows().withSuccess([
      ...mockSequenceFlows,
      {
        processInstanceId: '2251799813693731',
        activityId: 'SequenceFlow_1sz6737',
      },
    ]);

    eventListeners.online();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
      ]),
    );

    mockFetchSequenceFlows().withSuccess([
      ...mockSequenceFlows,
      {
        processInstanceId: '2251799813693731',
        activityId: 'SequenceFlow_1sz6737',
      },
    ]);

    eventListeners.online();
  });
});
