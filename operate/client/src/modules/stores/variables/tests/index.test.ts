/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {variablesStore} from '../';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {waitFor} from 'modules/testing-library';
import {
  createInstance,
  createOperation,
  createVariable,
} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockFetchVariable} from 'modules/mocks/api/fetchVariable';
import {mockGetOperation} from 'modules/mocks/api/getOperation';
import {mockVariableOperation, mockVariables} from './mocks';
import {getScopeId} from 'modules/utils/variables';

vi.mock('modules/constants/variables', async () => {
  const actual = await vi.importActual('modules/constants/variables');
  return {
    ...actual,
    MAX_VARIABLES_STORED: 5,
    MAX_VARIABLES_PER_REQUEST: 3,
  };
});

describe('stores/variables', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({id: '123', state: 'ACTIVE'}),
    );

    mockApplyOperation().withSuccess(mockVariableOperation);

    mockFetchVariables().withSuccess(mockVariables);
    mockGetOperation().withSuccess([createOperation({state: 'COMPLETED'})]);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
      flowNodeInstanceId: '123',
    });

    await processInstanceDetailsStore.fetchProcessInstance('123');
  });

  afterEach(() => {
    variablesStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
  });

  it('should remove variables with active operations if instance is canceled', async () => {
    variablesStore.init('1');

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: () => {},
    });

    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.pendingItem).toEqual({
      name: 'test',
      value: '1',
      hasActiveOperation: true,
      isFirst: false,
      isPreview: false,
      sortValues: null,
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'}),
    );

    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.pendingItem).toBe(null);
  });

  it('should poll variables when instance is running', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    variablesStore.init('123');

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables),
    );

    mockFetchVariables().withSuccess([
      ...mockVariables,
      createVariable({name: 'clientNo', value: '"CNT-1211132-02"'}),
    ]);

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813725337-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['clientNo'],
        },
      ]),
    );

    mockFetchVariables().withSuccess([
      ...mockVariables,
      createVariable({name: 'clientNo', value: '"CNT-1211132-02"'}),
      createVariable({name: 'orderNo', value: '"CMD0001-01"'}),
    ]);

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813725337-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['clientNo'],
        },
        {
          id: '2251799813725337-orderNo',
          name: 'orderNo',
          value: '"CMD0001-01"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['orderNo'],
        },
      ]),
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'}),
    );
    vi.runOnlyPendingTimers();
    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813725337-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['clientNo'],
        },
        {
          id: '2251799813725337-orderNo',
          name: 'orderNo',
          value: '"CMD0001-01"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['orderNo'],
        },
      ]),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should clear items', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.state.items).toEqual(mockVariables);
    variablesStore.clearItems();
    expect(variablesStore.state.items).toEqual([]);
  });

  it('should fetch variables', async () => {
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables),
    );
    expect(variablesStore.state.status).toBe('fetched');
  });

  it('should fetch variable', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    const mockOnError = vi.fn();

    // on success
    mockFetchVariable().withSuccess(createVariable({id: 'variable-id'}));

    expect(variablesStore.state.loadingItemId).toBeNull();
    variablesStore.fetchVariable({
      processInstanceId: 'some-process-instance-id',
      variableId: 'variable-id',
      onError: mockOnError,
    });
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).not.toHaveBeenCalled();

    // on server error
    mockFetchVariable().withServerError();

    variablesStore.fetchVariable({
      processInstanceId: 'some-process-instance-id',
      variableId: 'variable-id',
      onError: mockOnError,
    });
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).toHaveBeenCalledTimes(1);

    // on network error
    mockFetchVariable().withNetworkError();

    variablesStore.fetchVariable({
      processInstanceId: 'some-process-instance-id',
      variableId: 'variable-id',
      onError: mockOnError,
    });
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).toHaveBeenCalledTimes(2);

    mockFetchVariable().withNetworkError();

    expect(variablesStore.state.loadingItemId).toBeNull();
    variablesStore.fetchVariable({
      processInstanceId: 'some-process-instance-id',
      variableId: 'variable-id',
      onError: mockOnError,
      enableLoading: false,
    });

    expect(variablesStore.state.loadingItemId).toBeNull();
    await waitFor(() => expect(mockOnError).toHaveBeenCalledTimes(3));

    consoleErrorMock.mockRestore();
  });

  it('should get scopeId', async () => {
    expect(getScopeId()).toBe('123');
  });

  it('should get hasActiveOperation', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.hasActiveOperation).toBe(false);
    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: () => {},
    });
    expect(variablesStore.hasActiveOperation).toBe(true);
  });

  it('should get hasNoVariables', async () => {
    mockFetchVariables().withSuccess([]);

    // should be false when initial load is not complete
    expect(variablesStore.hasNoVariables).toBe(false);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.hasNoVariables).toBe(true);

    mockFetchVariables().withSuccess([]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    // should be false when loading
    expect(variablesStore.hasNoVariables).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
    expect(variablesStore.hasNoVariables).toBe(true);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    mockFetchVariables().withSuccess(mockVariables);

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.hasNoVariables).toBe(false);
  });

  it('should reset store', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: () => {},
    });

    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.latestFetch).toEqual({
      fetchType: 'initial',
      itemsCount: 3,
    });
    expect(variablesStore.state.pendingItem).toEqual({
      name: 'test',
      value: '1',
      isPreview: false,
      hasActiveOperation: true,
      isFirst: false,
      sortValues: null,
    });
    expect(variablesStore.state.status).toBe('fetched');
    variablesStore.reset();
    expect(variablesStore.state.items).toEqual([]);
    expect(variablesStore.state.latestFetch).toEqual({
      fetchType: null,
      itemsCount: 0,
    });
    expect(variablesStore.state.pendingItem).toBe(null);
    expect(variablesStore.state.status).toBe('initial');
  });

  it.skip('should not update state if store is reset when there are ongoing requests', async () => {
    mockFetchVariables().withSuccess(mockVariables);

    const variablesRequest = variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    variablesStore.reset();

    await variablesRequest;

    expect(variablesStore.state.status).toBe('initial');
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    variablesStore.init('1');

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables),
    );

    const newMockVariables = [
      ...mockVariables,
      createVariable({
        name: 'test',
        value: '1',
      }),
    ];

    mockFetchVariables().withSuccess(newMockVariables);

    eventListeners.online?.();

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(newMockVariables),
    );
  });

  it('should fetch prev/next variables', async () => {
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 3, scopeId: '1'},
    });
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables),
    );
    expect(variablesStore.state.status).toBe('fetched');

    expect(variablesStore.state.items[0]?.name).toBe('mwst');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name,
    ).toBe('paid');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);

    mockFetchVariables().withSuccess([
      createVariable({name: 'test1', value: '1'}),
      createVariable({name: 'test2', value: '2'}),
      createVariable({name: 'test3', value: '3'}),
    ]);

    variablesStore.fetchNextVariables('1');
    expect(variablesStore.state.status).toBe('fetching-next');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('orderStatus');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]!.name,
    ).toBe('test3');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(true);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);

    mockFetchVariables().withSuccess([
      createVariable({name: 'test4', value: '4'}),
      createVariable({name: 'test5', value: '5'}),
    ]);

    variablesStore.fetchNextVariables('1');

    expect(variablesStore.state.status).toBe('fetching-next');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('test1');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name,
    ).toBe('test5');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(true);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchPreviousVariables('1');
    expect(variablesStore.state.status).toBe('fetching-prev');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('mwst');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name,
    ).toBe('test2');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);
  });

  it('should get sort values', async () => {
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 3, scopeId: '1'},
    });
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables),
    );
    expect(variablesStore.state.status).toBe('fetched');

    expect(variablesStore.getSortValues('initial')).toBe(undefined);
    expect(variablesStore.getSortValues('prev')).toEqual(['mwst']);
    expect(variablesStore.getSortValues('next')).toEqual(['paid']);
  });

  it('should clear items when scope id is changed', async () => {
    variablesStore.init('id');

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables),
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'flow_node_without_running_scope',
    });

    await waitFor(() => expect(variablesStore.state.items).toEqual([]));
  });
});
