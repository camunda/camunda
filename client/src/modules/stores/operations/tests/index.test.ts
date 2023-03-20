/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {operationsStore} from '../';
import {waitFor} from 'modules/testing-library';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {operations} from 'modules/testUtils';

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
    jest.useFakeTimers();
    await waitFor(() => expect(operationsStore.state.status).toBe('fetched'));

    // no polling occurs in the next 2 polling
    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

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
      expect(operationsStore.hasRunningOperations).toBe(true)
    );

    mockFetchBatchOperations().withSuccess([
      {...runningOperation, endDate: '2020-09-2930T15:38:34.372+0000'},
      ...operations,
    ]);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(operationsStore.hasRunningOperations).toBe(false)
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    mockFetchBatchOperations().withSuccess(operations);

    operationsStore.init();

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched')
    );

    mockFetchBatchOperations().withSuccess(operations);

    eventListeners.online();

    expect(operationsStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched')
    );

    window.addEventListener = originalEventListener;
  });
});
