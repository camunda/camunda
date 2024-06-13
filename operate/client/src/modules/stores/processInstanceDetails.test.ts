/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceDetailsStore} from './processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {createOperation} from 'modules/utils/instance';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';

const currentInstanceMock = createInstance();

jest.unmock('modules/utils/date/formatDate');

describe('stores/currentInstance', () => {
  afterEach(() => {
    processInstanceDetailsStore.reset();
  });

  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(currentInstanceMock);
  });

  it('should fetch current instance on init state', async () => {
    processInstanceDetailsStore.init({id: '1'});
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );
  });

  it('should poll if current instance is running', async () => {
    jest.useFakeTimers();
    processInstanceDetailsStore.init({id: '1'});
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );

    const secondCurrentInstanceMock = createInstance();

    mockFetchProcessInstance().withSuccess(secondCurrentInstanceMock);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        secondCurrentInstanceMock,
      ),
    );

    const thirdCurrentInstanceMock = createInstance();

    mockFetchProcessInstance().withSuccess(thirdCurrentInstanceMock);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        thirdCurrentInstanceMock,
      ),
    );

    const finishedCurrentInstanceMock = createInstance({state: 'CANCELED'});

    mockFetchProcessInstance().withSuccess(finishedCurrentInstanceMock);

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        finishedCurrentInstanceMock,
      ),
    );

    // do not poll since instance is not running anymore
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        finishedCurrentInstanceMock,
      ),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set current instance', async () => {
    const mockInstance = createInstance({id: '123', state: 'ACTIVE'});
    expect(processInstanceDetailsStore.state.processInstance).toEqual(null);
    processInstanceDetailsStore.setProcessInstance(mockInstance);
    expect(processInstanceDetailsStore.state.processInstance).toEqual(
      mockInstance,
    );
  });

  it('should get process title', async () => {
    expect(processInstanceDetailsStore.processTitle).toBe(null);
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processName: 'processName',
      }),
    );
    expect(processInstanceDetailsStore.processTitle).toBe(
      'Operate: Process Instance 123 of processName',
    );
  });

  it('should reset store', async () => {
    const mockInstance = createInstance({
      id: '123',
      state: 'ACTIVE',
      processName: 'processName',
    });

    expect(processInstanceDetailsStore.processTitle).toBe(null);
    processInstanceDetailsStore.setProcessInstance(mockInstance);
    expect(processInstanceDetailsStore.state.processInstance).toEqual(
      mockInstance,
    );
    processInstanceDetailsStore.reset();
    expect(processInstanceDetailsStore.processTitle).toBe(null);
  });

  it('should set active operation state', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        hasActiveOperation: false,
        operations: [],
      }),
    );

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(false);
    processInstanceDetailsStore.activateOperation('CANCEL_PROCESS_INSTANCE');

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(true);
    expect(
      processInstanceDetailsStore.state.processInstance?.operations,
    ).toEqual([createOperation('CANCEL_PROCESS_INSTANCE')]);

    processInstanceDetailsStore.deactivateOperation('CANCEL_PROCESS_INSTANCE');

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(false);
    expect(
      processInstanceDetailsStore.state.processInstance?.operations,
    ).toEqual([]);
  });

  it('should not set active operation state to false if there are still running operations', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        hasActiveOperation: false,
      }),
    );

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(false);
    processInstanceDetailsStore.activateOperation('CANCEL_PROCESS_INSTANCE');

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(true);

    expect(
      processInstanceDetailsStore.state.processInstance?.operations,
    ).toEqual([
      {
        batchOperationId: 'fe19ed17-a213-4b8d-ad10-2fb6d2bd89e5',
        errorMessage: 'string',
        id: 'id_27',
        state: 'SENT',
        type: 'RESOLVE_INCIDENT',
        completedDate: null,
      },
      createOperation('CANCEL_PROCESS_INSTANCE'),
    ]);

    processInstanceDetailsStore.deactivateOperation('CANCEL_PROCESS_INSTANCE');

    expect(
      processInstanceDetailsStore.state.processInstance?.hasActiveOperation,
    ).toBe(true);
    expect(
      processInstanceDetailsStore.state.processInstance?.operations,
    ).toEqual([
      {
        batchOperationId: 'fe19ed17-a213-4b8d-ad10-2fb6d2bd89e5',
        errorMessage: 'string',
        id: 'id_27',
        state: 'SENT',
        type: 'RESOLVE_INCIDENT',
        completedDate: null,
      },
    ]);
  });

  it('should get latest migration date', () => {
    const LATEST_MIGRATION_DATE = '2022-02-02T12:00:00.000+0000';

    processInstanceDetailsStore.setProcessInstance({
      ...createInstance({
        id: '123',
        hasActiveOperation: false,
      }),
      // unsorted operations
      operations: [
        {
          ...createOperation('MIGRATE_PROCESS_INSTANCE'),
          completedDate: '2022-01-01T11:00:00.000+0000',
          state: 'COMPLETED',
        },
        // latest completed migrate operation
        {
          ...createOperation('MIGRATE_PROCESS_INSTANCE'),
          completedDate: LATEST_MIGRATION_DATE,
          state: 'COMPLETED',
        },
        {
          ...createOperation('DELETE_PROCESS_INSTANCE'),
          completedDate: '2023-01-01T17:55:00.000+0000',
          state: 'COMPLETED',
        },
        {
          ...createOperation('MIGRATE_PROCESS_INSTANCE'),
          completedDate: null,
          state: 'SENT',
        },
        {
          ...createOperation('MIGRATE_PROCESS_INSTANCE'),
          completedDate: '2022-01-01T17:55:00.000+0000',
          state: 'COMPLETED',
        },
      ],
    });

    expect(processInstanceDetailsStore.latestMigrationDate).toBe(
      LATEST_MIGRATION_DATE,
    );
  });

  it('should return undefined if there is no migration', () => {
    processInstanceDetailsStore.setProcessInstance({
      ...createInstance({
        id: '123',
        hasActiveOperation: false,
      }),
      // unsorted operations
      operations: [
        {
          ...createOperation('DELETE_PROCESS_INSTANCE'),
          completedDate: '2023-01-01T17:55:00.000+0000',
          state: 'COMPLETED',
        },
        {
          ...createOperation('MIGRATE_PROCESS_INSTANCE'),
          completedDate: null,
          state: 'SENT',
        },
      ],
    });

    expect(processInstanceDetailsStore.latestMigrationDate).toBeUndefined();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStore.init({id: '1'});

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );

    mockFetchProcessInstance().withSuccess({
      ...currentInstanceMock,
      state: 'INCIDENT',
    });

    eventListeners.online();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual({
        ...currentInstanceMock,
        state: 'INCIDENT',
      }),
    );

    window.addEventListener = originalEventListener;
  });

  it('should poll with polling header', async () => {
    jest.useFakeTimers();

    // expect the first request not to be a polling request
    mockFetchProcessInstance().withSuccess(currentInstanceMock, {
      expectPolling: false,
    });

    processInstanceDetailsStore.init({id: '1'});
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        currentInstanceMock,
      ),
    );

    const secondCurrentInstanceMock = createInstance();

    // expect the second request to be a polling request
    mockFetchProcessInstance().withSuccess(secondCurrentInstanceMock, {
      expectPolling: true,
    });

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstanceDetailsStore.state.processInstance).toEqual(
        secondCurrentInstanceMock,
      ),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
