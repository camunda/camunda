/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {variablesStore} from './variables';
import {currentInstanceStore} from './currentInstance';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';

describe('stores/variables', () => {
  const mockVariables = [
    {
      id: '2251799813686374-mwst',
      name: 'mwst',
      value: '63.27',
      scopeId: '2251799813686374',
      processInstanceId: '2251799813686374',
      hasActiveOperation: false,
    },
    {
      id: '2251799813686374-orderStatus',
      name: 'orderStatus',
      value: '"NEW"',
      scopeId: '2251799813686374',
      processInstanceId: '2251799813686374',
      hasActiveOperation: false,
    },
    {
      id: '2251799813686374-paid',
      name: 'paid',
      value: 'true',
      scopeId: '2251799813686374',
      processInstanceId: '2251799813686374',
      hasActiveOperation: false,
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

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json(mockVariables))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json(mockVariableOperation))
      )
    );
  });

  afterEach(() => {
    variablesStore.reset();
    currentInstanceStore.reset();
    flowNodeSelectionStore.reset();
  });

  it('should remove variables with active operations if instance is canceled', async () => {
    variablesStore.init('1');
    await Promise.all([
      variablesStore.fetchVariables('1'),
      variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onError: () => {},
      }),
    ]);

    expect(variablesStore.state.items).toEqual([
      ...mockVariables,
      {
        hasActiveOperation: true,
        name: 'test',
        value: '1',
        processInstanceId: '1',
      },
    ]);
    currentInstanceStore.setCurrentInstance({id: '123', state: 'CANCELED'});
    expect(variablesStore.state.items).toEqual(mockVariables);
  });

  it('should poll variables when instance is running', async () => {
    variablesStore.init('1');

    jest.useFakeTimers();
    currentInstanceStore.setCurrentInstance({id: '123', state: 'ACTIVE'});
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );

    mockServer.use(
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
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
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
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

    currentInstanceStore.setCurrentInstance({id: '123', state: 'CANCELED'});
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
    await variablesStore.fetchVariables('1');
    expect(variablesStore.state.items).toEqual(mockVariables);
    variablesStore.clearItems();
    expect(variablesStore.state.items).toEqual([]);
  });

  it('should fetch variables', async () => {
    variablesStore.fetchVariables('1');
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );
    expect(variablesStore.state.status).toBe('fetched');
  });

  describe('Add Variable', () => {
    it('should add variable', async () => {
      expect(variablesStore.state.items).toEqual([]);
      await variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onError: () => {},
      });
      expect(variablesStore.state.items).toEqual([
        {
          name: 'test',
          value: '1',
          hasActiveOperation: true,
          processInstanceId: '1',
        },
      ]);

      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/operation',
          (_, res, ctx) => res.once(ctx.json(mockVariableOperation))
        )
      );

      await variablesStore.addVariable({
        id: '1',
        name: 'test2',
        value: '"value"',
        onError: () => {},
      });
      expect(variablesStore.state.items).toEqual([
        {
          name: 'test',
          value: '1',
          hasActiveOperation: true,
          processInstanceId: '1',
        },
        {
          name: 'test2',
          value: '"value"',
          hasActiveOperation: true,
          processInstanceId: '1',
        },
      ]);
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
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual([]);
      expect(mockOnError).toHaveBeenCalled();
    });
  });

  describe('Update Variable', () => {
    it('should update variable', async () => {
      await variablesStore.fetchVariables('1');
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
          name: 'mwst',
          value: '65',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: true,
        },
        {
          id: '2251799813686374-orderStatus',
          name: 'orderStatus',
          value: '"NEW"',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
        {
          id: '2251799813686374-paid',
          name: 'paid',
          value: 'true',
          scopeId: '2251799813686374',
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
          name: 'mwst',
          value: '65',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: true,
        },
        {
          id: '2251799813686374-orderStatus',
          name: 'orderStatus',
          value: '"NEW"',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: false,
        },
        {
          id: '2251799813686374-paid',
          name: 'paid',
          value: 'false',
          scopeId: '2251799813686374',
          processInstanceId: '2251799813686374',
          hasActiveOperation: true,
        },
      ]);
    });

    it('should not update variable on server error', async () => {
      await variablesStore.fetchVariables('1');
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
      await variablesStore.fetchVariables('1');
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
    expect(variablesStore.scopeId).toBe(undefined);
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
      flowNodeInstanceId: '123',
    });
    expect(variablesStore.scopeId).toBe('123');
  });

  it('should get hasActiveOperation', async () => {
    await variablesStore.fetchVariables('1');
    expect(variablesStore.hasActiveOperation).toBe(false);
    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onError: () => {},
    });
    expect(variablesStore.hasActiveOperation).toBe(true);
  });

  it('should get hasNoVariables', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    // should be false when initial load is not complete
    expect(variablesStore.hasNoVariables).toBe(false);
    variablesStore.fetchVariables('1');

    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.hasNoVariables).toBe(true);

    mockServer.use(
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    variablesStore.fetchVariables('1');

    // should be false when loading
    expect(variablesStore.hasNoVariables).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
    expect(variablesStore.hasNoVariables).toBe(true);

    variablesStore.fetchVariables('1');

    mockServer.use(
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json(mockVariables))
      )
    );
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.hasNoVariables).toBe(false);
  });

  it('should reset store', async () => {
    await variablesStore.fetchVariables('1');
    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.status).toBe('fetched');
    variablesStore.reset();
    expect(variablesStore.state.items).toEqual([]);
    expect(variablesStore.state.status).toBe('initial');
  });

  it('should not update state if store is reset when there are ongoing requests', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json(mockVariables))
      )
    );

    const variablesRequest = variablesStore.fetchVariables('1');
    variablesStore.reset();
    expect(variablesStore.shouldCancelOngoingRequests).toBe(true);
    await variablesRequest;
    expect(variablesStore.shouldCancelOngoingRequests).toBe(false);
    expect(variablesStore.state.status).toBe('initial');
  });

  it('should manage local and server variables correctly', async () => {
    expect(variablesStore.state.items).toEqual([]);

    await variablesStore.fetchVariables('1');
    expect(variablesStore.state.items).toEqual(mockVariables);
    await variablesStore.addVariable({
      id: '1',
      name: 'test1',
      value: '123',
      onError: () => {},
    });

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json(mockVariableOperation))
      )
    );

    await variablesStore.updateVariable({
      id: '1',
      name: 'paid',
      value: 'false',
      onError: () => {},
    });

    mockServer.use(
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '2251799813686374-mwst',
              name: 'mwst',
              value: '63.27',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
            },
            {
              id: '2251799813686374-orderStatus',
              name: 'orderStatus',
              value: '"NEW"',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
            },
            {
              id: '2251799813686374-paid',
              name: 'paid',
              value: 'true',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
            },
            {
              id: '2251799813686374-orderNo',
              name: 'someNewVariableFromServer',
              value: '"CMD0001-01"',
              scopeId: '2251799813686374',
              processInstanceId: '2251799813686374',
              hasActiveOperation: false,
            },
          ])
        )
      )
    );

    await variablesStore.fetchVariables('1');

    expect(variablesStore.state.items).toEqual([
      {
        hasActiveOperation: false,
        id: '2251799813686374-mwst',
        name: 'mwst',
        scopeId: '2251799813686374',
        value: '63.27',
        processInstanceId: '2251799813686374',
      },
      {
        hasActiveOperation: false,
        id: '2251799813686374-orderStatus',
        name: 'orderStatus',
        scopeId: '2251799813686374',
        value: '"NEW"',
        processInstanceId: '2251799813686374',
      },
      {
        hasActiveOperation: false,
        id: '2251799813686374-orderNo',
        name: 'someNewVariableFromServer',
        scopeId: '2251799813686374',
        value: '"CMD0001-01"',
        processInstanceId: '2251799813686374',
      },
      {
        id: '2251799813686374-paid',
        name: 'paid',
        value: 'false',
        scopeId: '2251799813686374',
        processInstanceId: '2251799813686374',
        hasActiveOperation: true,
      },
      {
        hasActiveOperation: true,
        name: 'test1',
        value: '123',
        processInstanceId: '1',
      },
    ]);
  });
});
