/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {variablesStore} from './variables';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from 'modules/testing-library';
import {createInstance} from 'modules/testUtils';

jest.mock('modules/constants/variables', () => ({
  ...jest.requireActual('modules/constants/variables'),
  MAX_VARIABLES_STORED: 5,
  MAX_VARIABLES_PER_REQUEST: 3,
}));

describe('stores/variables', () => {
  const mockVariables = [
    {
      id: '2251799813686374-mwst',
      name: 'mwst',
      value: '63.27',
      scopeId: '2251799813686374',
      processInstanceId: '2251799813686374',
      hasActiveOperation: false,
      isFirst: true,
      sortValues: ['mwst'],
    },
    {
      id: '2251799813686374-orderStatus',
      name: 'orderStatus',
      value: '"NEW"',
      scopeId: '2251799813686374',
      processInstanceId: '2251799813686374',
      hasActiveOperation: false,
      isFirst: false,
      sortValues: ['orderStatus'],
    },
    {
      id: '2251799813686374-paid',
      name: 'paid',
      value: 'true',
      scopeId: '2251799813686374',
      processInstanceId: '2251799813686374',
      hasActiveOperation: false,
      isFirst: false,
      sortValues: ['paid'],
    },
  ];

  const mockVariableOperation = {
    id: 'b638e93a-5083-4487-af9c-78cac528a07a',
    name: null,
    type: 'UPDATE_VARIABLE',
    startDate: '2020-10-09T08:30:29.749+0000',
    endDate: null,
    username: 'demo',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 0,
  };

  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId', (_, res, ctx) =>
        res.once(ctx.json({id: '123', state: 'ACTIVE'}))
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json(mockVariables))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json(mockVariableOperation))
      ),
      rest.get('/api/operations', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              state: 'COMPLETED',
            },
          ])
        )
      )
    );

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
      createInstance({id: '123', state: 'CANCELED'})
    );

    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.pendingItem).toBe(null);
  });

  it('should poll variables when instance is running', async () => {
    jest.useFakeTimers();

    variablesStore.init('123');

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            ...mockVariables,
            {
              id: '2251799813686374-clientNo',
              name: 'clientNo',
              value: '"CNT-1211132-02"',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
            },
          ])
        )
      )
    );
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813686374-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
      ])
    );

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            ...mockVariables,
            {
              id: '2251799813686374-clientNo',
              name: 'clientNo',
              value: '"CNT-1211132-02"',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
            },
            {
              id: '2251799813686374-orderNo',
              name: 'orderNo',
              value: '"CMD0001-01"',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
            },
          ])
        )
      )
    );
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813686374-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
        {
          id: '2251799813686374-orderNo',
          name: 'orderNo',
          value: '"CMD0001-01"',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
      ])
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'})
    );
    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813686374-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
        {
          id: '2251799813686374-orderNo',
          name: 'orderNo',
          value: '"CMD0001-01"',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
      ])
    );

    jest.clearAllTimers();
    jest.useRealTimers();
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
      expect(variablesStore.state.items).toEqual(mockVariables)
    );
    expect(variablesStore.state.status).toBe('fetched');
  });

  it('should fetch variable', async () => {
    const mockOnError = jest.fn();

    // on success
    mockServer.use(
      rest.get('/api/variables/:variableId', (_, res, ctx) =>
        res.once(ctx.json({id: 'variable-id', state: 'ACTIVE'}))
      )
    );

    expect(variablesStore.state.loadingItemId).toBeNull();
    variablesStore.fetchVariable({id: 'variable-id', onError: mockOnError});
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).not.toHaveBeenCalled();

    // on server error
    mockServer.use(
      rest.get('/api/variables/:variableId', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({}))
      )
    );

    variablesStore.fetchVariable({id: 'variable-id', onError: mockOnError});
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).toHaveBeenCalledTimes(1);

    // on network error

    mockServer.use(
      rest.get('/api/variables/:variableId', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );

    variablesStore.fetchVariable({id: 'variable-id', onError: mockOnError});
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).toHaveBeenCalledTimes(2);

    mockServer.use(
      rest.get('/api/variables/:variableId', (_, res, ctx) =>
        res.once(ctx.json({id: 'variable-id', state: 'ACTIVE'}))
      )
    );

    expect(variablesStore.state.loadingItemId).toBeNull();
    variablesStore.fetchVariable({
      id: 'variable-id',
      onError: mockOnError,
      enableLoading: false,
    });
    expect(variablesStore.state.loadingItemId).toBeNull();
  });

  describe('Add Variable', () => {
    it('should add variable', async () => {
      expect(variablesStore.state.items).toEqual([]);
      expect(variablesStore.state.pendingItem).toBe(null);

      await variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onSuccess: () => {},
        onError: () => {},
      });

      expect(variablesStore.state.items).toEqual([]);
      expect(variablesStore.state.pendingItem).toEqual({
        name: 'test',
        value: '1',
        isPreview: false,
        hasActiveOperation: true,
        isFirst: false,
        sortValues: null,
      });
    });

    it('should not add variable on server error', async () => {
      expect(variablesStore.state.items).toEqual([]);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/operation',
          (_, res, ctx) =>
            res.once(ctx.status(500), ctx.json({error: 'An error occured'}))
        )
      );

      const mockOnError = jest.fn();
      await variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onSuccess: () => {},
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual([]);
      expect(mockOnError).toHaveBeenCalled();
    });

    it('should not add variable on network error', async () => {
      expect(variablesStore.state.items).toEqual([]);

      mockServer.use(
        rest.post('/api/process-instances/:instanceId/operation', (_, res) =>
          res.networkError('A network error')
        )
      );

      const mockOnError = jest.fn();
      await variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onSuccess: () => {},
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual([]);
      expect(mockOnError).toHaveBeenCalled();
    });
  });

  describe('Update Variable', () => {
    it('should update variable', async () => {
      await variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });
      expect(variablesStore.state.items).toEqual(mockVariables);
      await variablesStore.updateVariable({
        id: '1',
        name: 'mwst',
        value: '65',
        onError: () => {},
      });
      expect(variablesStore.state.items).toEqual([
        {
          id: '2251799813686374-mwst',
          isFirst: true,
          name: 'mwst',
          value: '65',
          scopeId: '2251799813686374',
          sortValues: ['mwst'],
          processInstanceId: '2251799813686374',
          hasActiveOperation: true,
        },
        {
          id: '2251799813686374-orderStatus',
          isFirst: false,
          name: 'orderStatus',
          value: '"NEW"',
          scopeId: '2251799813686374',
          sortValues: ['orderStatus'],
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
        {
          id: '2251799813686374-paid',
          isFirst: false,
          name: 'paid',
          value: 'true',
          scopeId: '2251799813686374',
          sortValues: ['paid'],
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
      ]);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/operation',
          (_, res, ctx) => res.once(ctx.json(mockVariableOperation))
        )
      );

      await variablesStore.updateVariable({
        id: '1',
        name: 'paid',
        value: 'false',
        onError: () => {},
      });
      expect(variablesStore.state.items).toEqual([
        {
          id: '2251799813686374-mwst',
          isFirst: true,
          name: 'mwst',
          value: '65',
          scopeId: '2251799813686374',
          sortValues: ['mwst'],
          processInstanceId: '2251799813686374',
          hasActiveOperation: true,
        },
        {
          id: '2251799813686374-orderStatus',
          isFirst: false,
          name: 'orderStatus',
          value: '"NEW"',
          scopeId: '2251799813686374',
          sortValues: ['orderStatus'],
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
        {
          id: '2251799813686374-paid',
          isFirst: false,
          name: 'paid',
          value: 'false',
          scopeId: '2251799813686374',
          sortValues: ['paid'],
          processInstanceId: '2251799813686374',
          hasActiveOperation: true,
        },
      ]);
    });

    it('should not update variable on server error', async () => {
      await variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });
      expect(variablesStore.state.items).toEqual(mockVariables);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/operation',
          (_, res, ctx) =>
            res.once(ctx.status(500), ctx.json({error: 'An error occured'}))
        )
      );

      const mockOnError = jest.fn();
      await variablesStore.updateVariable({
        id: '1',
        name: 'mwst',
        value: '65',
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual(mockVariables);
      expect(mockOnError).toHaveBeenCalled();
    });

    it('should not update variable on network error', async () => {
      await variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });
      expect(variablesStore.state.items).toEqual(mockVariables);

      mockServer.use(
        rest.post('/api/process-instances/:instanceId/operation', (_, res) =>
          res.networkError('A network error')
        )
      );

      const mockOnError = jest.fn();
      await variablesStore.updateVariable({
        id: '1',
        name: 'mwst',
        value: '65',
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual(mockVariables);
      expect(mockOnError).toHaveBeenCalled();
    });
  });

  it('should get scopeId', async () => {
    expect(variablesStore.scopeId).toBe('123');
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
    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

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

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

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

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json(mockVariables))
      )
    );
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

  it('should not update state if store is reset when there are ongoing requests', async () => {
    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json(mockVariables))
      )
    );

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
    const eventListeners: Record<string, Function> = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    variablesStore.init('1');

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );

    const newMockVariables = [
      ...mockVariables,
      {
        hasActiveOperation: false,
        name: 'test',
        value: '1',
        processInstanceId: '1',
      },
    ];

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json(newMockVariables))
      )
    );

    eventListeners.online?.();

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(newMockVariables)
    );

    window.addEventListener = originalEventListener;
  });

  it('should fetch prev/next variables', async () => {
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 3, scopeId: '1'},
    });
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );
    expect(variablesStore.state.status).toBe('fetched');

    expect(variablesStore.state.items[0]?.name).toBe('mwst');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name
    ).toBe('paid');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '2251799813686374-test1',
              name: 'test1',
              value: '1',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
              isFirst: false,
            },
            {
              id: '2251799813686374-test2',
              name: 'test2',
              value: '2',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
              isFirst: false,
            },
            {
              id: '2251799813686374-test3',
              name: 'test3',
              value: '3',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
              isFirst: false,
            },
          ])
        )
      )
    );

    variablesStore.fetchNextVariables('1');
    expect(variablesStore.state.status).toBe('fetching-next');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('orderStatus');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]!.name
    ).toBe('test3');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(true);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '2251799813686374-test4',
              name: 'test4',
              value: '4',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
              isFirst: false,
            },
            {
              id: '2251799813686374-test5',
              name: 'test5',
              value: '5',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
              isFirst: false,
            },
          ])
        )
      )
    );

    variablesStore.fetchNextVariables('1');

    expect(variablesStore.state.status).toBe('fetching-next');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('test1');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name
    ).toBe('test5');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(true);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json(mockVariables))
      )
    );

    variablesStore.fetchPreviousVariables('1');
    expect(variablesStore.state.status).toBe('fetching-prev');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('mwst');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name
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
      expect(variablesStore.state.items).toEqual(mockVariables)
    );
    expect(variablesStore.state.status).toBe('fetched');

    expect(variablesStore.getSortValues('initial')).toBe(undefined);
    expect(variablesStore.getSortValues('prev')).toEqual(['mwst']);
    expect(variablesStore.getSortValues('next')).toEqual(['paid']);
  });
});
