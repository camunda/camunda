/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {operationsStore} from '../';
import {waitFor} from 'modules/testing-library';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {operations} from 'modules/testUtils';
import type {OperationEntity} from 'modules/types/operate';

describe('stores/operations', () => {
  afterEach(() => {
    operationsStore.reset();
  });

  it('should reset state', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);
    expect(operationsStore.state.status).toBe('fetched');
    operationsStore.reset();
    expect(operationsStore.state.operations).toEqual([]);
    expect(operationsStore.state.status).toEqual('initial');
  });

  it('should set hasMoreOperations', async () => {
    expect(operationsStore.state.hasMoreOperations).toBe(true);
    operationsStore.setHasMoreOperations(10);
    expect(operationsStore.state.hasMoreOperations).toBe(false);
    operationsStore.setHasMoreOperations(20);
    expect(operationsStore.state.hasMoreOperations).toBe(true);
  });

  it('should increase page if next operations are requested', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchOperations();
    expect(operationsStore.state.page).toBe(1);

    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchNextOperations();
    expect(operationsStore.state.page).toBe(2);

    await waitFor(() => expect(operationsStore.state.status).toBe('fetched'));
  });

  it('should increase page', () => {
    expect(operationsStore.state.page).toBe(1);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(2);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(3);
  });

  it('should get hasRunningOperations', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    await operationsStore.fetchOperations();
    expect(operationsStore.hasRunningOperations).toBe(false);

    mockFetchBatchOperations().withSuccess([
      {
        id: '6255ced4-f570-46ce-b5c0-4b88a785fb9a',
        name: null,
        type: 'RESOLVE_INCIDENT',
        startDate: '2020-09-30T06:14:55.185+0000',
        endDate: null,
        instancesCount: 2,
        operationsTotalCount: 0,
        operationsFinishedCount: 0,
        sortValues: ['1601446495209', '1601446495185'],
      },
      ...operations,
    ]);

    await operationsStore.fetchOperations();
    expect(operationsStore.hasRunningOperations).toBe(true);
  });

  it('should poll when there are running operations', async () => {
    mockFetchBatchOperations().withSuccess(operations);

    operationsStore.init();
    vi.useFakeTimers({shouldAdvanceTime: true});
    await waitFor(() => expect(operationsStore.state.status).toBe('fetched'));

    // no polling occurs in the next 2 polling
    vi.runOnlyPendingTimers();
    vi.runOnlyPendingTimers();

    const runningOperation: OperationEntity = {
      id: '6255ced4-f570-46ce-b5c0-4b88a785fb9a',
      name: null,
      type: 'RESOLVE_INCIDENT',
      startDate: '2020-09-30T06:14:55.185+0000',
      endDate: null,
      instancesCount: 2,
      operationsTotalCount: 0,
      operationsFinishedCount: 0,
      sortValues: ['1601446495209', '1601446495185'],
    };

    const operationsWithRunningOperation: OperationEntity[] = [
      runningOperation,
      ...operations,
    ];
    mockFetchBatchOperations().withSuccess(operationsWithRunningOperation);

    operationsStore.fetchOperations();
    await waitFor(() =>
      expect(operationsStore.hasRunningOperations).toBe(true),
    );

    mockFetchBatchOperations().withSuccess([
      {...runningOperation, endDate: '2020-09-2930T15:38:34.372+0000'},
      ...operations,
    ]);

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(operationsStore.hasRunningOperations).toBe(false),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    mockFetchBatchOperations().withSuccess(operations);

    operationsStore.init();

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched'),
    );

    mockFetchBatchOperations().withSuccess(operations);

    eventListeners.online();

    expect(operationsStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched'),
    );
  });
});
