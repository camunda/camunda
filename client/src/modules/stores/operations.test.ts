/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {operationsStore} from './operations';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from 'modules/testing-library';
import {operations} from 'modules/testUtils';

describe('stores/operations', () => {
  afterEach(() => {
    operationsStore.reset();
  });

  it('should reset state', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );

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
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      ),
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );

    await operationsStore.fetchOperations();
    expect(operationsStore.state.page).toBe(1);

    await operationsStore.fetchNextOperations();
    expect(operationsStore.state.page).toBe(2);
  });

  describe('Apply Operation', () => {
    it('should prepend operations when an operation is applied', async () => {
      const newOperation = {
        id: 'c6cde799-69bc-4dd5-9f98-3f931aa2c922',
        name: null,
        type: 'CANCEL_PROCESS_INSTANCE',
        startDate: '2020-09-30T06:13:21.312+0000',
        endDate: null,
        username: 'demo',
        instancesCount: 1,
        operationsTotalCount: 1,
        operationsFinishedCount: 0,
      };
      mockServer.use(
        rest.post('/api/batch-operations', (_, res, ctx) =>
          res.once(ctx.json(operations))
        )
      );
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/operation',
          (_, res, ctx) => res.once(ctx.json(newOperation))
        )
      );

      await operationsStore.fetchOperations();
      expect(operationsStore.state.operations).toEqual(operations);

      await operationsStore.applyOperation({
        instanceId: '1',
        payload: {operationType: 'CANCEL_PROCESS_INSTANCE'},
        onError: () => {},
      });
      expect(operationsStore.state.operations).toEqual([
        newOperation,
        ...operations,
      ]);
    });

    it('should not prepend operations and call error callback when a server error occured', async () => {
      mockServer.use(
        rest.post('/api/batch-operations', (_, res, ctx) =>
          res.once(ctx.json(operations))
        )
      );
      mockServer.use(
        rest.post(
          '/api/process-instances/:instanceId/operation',
          (_, res, ctx) =>
            res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
        )
      );

      await operationsStore.fetchOperations();
      expect(operationsStore.state.operations).toEqual(operations);

      const mockOnError = jest.fn();

      await operationsStore.applyOperation({
        instanceId: '1',
        payload: {operationType: 'CANCEL_PROCESS_INSTANCE'},
        onError: mockOnError,
      });
      expect(operationsStore.state.operations).toEqual(operations);
      expect(mockOnError).toHaveBeenCalled();
    });

    it('should not prepend operations and call error callback when a network error occured', async () => {
      mockServer.use(
        rest.post('/api/batch-operations', (_, res, ctx) =>
          res.once(ctx.json(operations))
        )
      );
      mockServer.use(
        rest.post('/api/process-instances/:instanceId/operation', (_, res) =>
          res.networkError('A network error')
        )
      );

      await operationsStore.fetchOperations();
      expect(operationsStore.state.operations).toEqual(operations);

      const mockOnError = jest.fn();

      await operationsStore.applyOperation({
        instanceId: '1',
        payload: {operationType: 'CANCEL_PROCESS_INSTANCE'},
        onError: mockOnError,
      });
      expect(operationsStore.state.operations).toEqual(operations);
      expect(mockOnError).toHaveBeenCalled();
    });
  });

  describe('Apply Batch Operation', () => {
    it('should prepend operations when a batch operation is applied', async () => {
      const newOperation = {
        id: '6255ced4-f570-46ce-b5c0-4b88a785fb9a',
        name: null,
        type: 'RESOLVE_INCIDENT',
        startDate: '2020-09-30T06:14:55.185+0000',
        endDate: '2020-09-30T06:14:55.209+0000',
        username: 'demo',
        instancesCount: 2,
        operationsTotalCount: 0,
        operationsFinishedCount: 0,
      };
      mockServer.use(
        rest.post('/api/batch-operations', (_, res, ctx) =>
          res.once(ctx.json(operations))
        )
      );
      mockServer.use(
        rest.post('/api/process-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.json(newOperation))
        )
      );

      await operationsStore.fetchOperations();
      expect(operationsStore.state.operations).toEqual(operations);

      const mockOnSuccess = jest.fn();
      await operationsStore.applyBatchOperation({
        operationType: 'CANCEL_PROCESS_INSTANCE',
        query: {ids: [], excludeIds: []},
        onSuccess: mockOnSuccess,
        onError: () => {},
      });
      expect(operationsStore.state.operations).toEqual([
        newOperation,
        ...operations,
      ]);
      expect(mockOnSuccess).toHaveBeenCalled();
    });

    it('should not prepend operations and call error callback when a server error occured', async () => {
      mockServer.use(
        rest.post('/api/batch-operations', (_, res, ctx) =>
          res.once(ctx.json(operations))
        )
      );
      mockServer.use(
        rest.post('/api/process-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
        )
      );

      await operationsStore.fetchOperations();
      expect(operationsStore.state.operations).toEqual(operations);

      const mockOnError = jest.fn();

      await operationsStore.applyBatchOperation({
        operationType: 'CANCEL_PROCESS_INSTANCE',
        query: {ids: [], excludeIds: []},
        onSuccess: jest.fn(),
        onError: mockOnError,
      });

      expect(operationsStore.state.operations).toEqual(operations);
      expect(mockOnError).toHaveBeenCalled();
    });

    it('should not prepend operations and call error callback when a network error occured', async () => {
      mockServer.use(
        rest.post('/api/batch-operations', (_, res, ctx) =>
          res.once(ctx.json(operations))
        )
      );
      mockServer.use(
        rest.post('/api/process-instances/batch-operation', (_, res) =>
          res.networkError('A network error')
        )
      );

      await operationsStore.fetchOperations();
      expect(operationsStore.state.operations).toEqual(operations);

      const mockOnError = jest.fn();

      await operationsStore.applyBatchOperation({
        operationType: 'CANCEL_PROCESS_INSTANCE',
        query: {ids: [], excludeIds: []},
        onSuccess: jest.fn(),
        onError: mockOnError,
      });

      expect(operationsStore.state.operations).toEqual(operations);
      expect(mockOnError).toHaveBeenCalled();
    });
  });

  it('should increase page', () => {
    expect(operationsStore.state.page).toBe(1);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(2);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(3);
  });

  it('should get hasRunningOperations', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );

    await operationsStore.fetchOperations();
    expect(operationsStore.hasRunningOperations).toBe(false);

    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(
          ctx.json([
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
          ])
        )
      )
    );

    await operationsStore.fetchOperations();
    expect(operationsStore.hasRunningOperations).toBe(true);
  });

  it('should poll when there are running operations', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );

    operationsStore.init();
    jest.useFakeTimers();
    await waitFor(() => expect(operationsStore.state.status).toBe('fetched'));

    // no polling occurs in the next 2 polling
    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    const operationsWithRunningOperation = [
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
    ];

    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operationsWithRunningOperation))
      )
    );

    operationsStore.fetchOperations();
    await waitFor(() =>
      expect(operationsStore.hasRunningOperations).toBe(true)
    );

    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '6255ced4-f570-46ce-b5c0-4b88a785fb9a',
              endDate: '2020-09-30T06:14:55.185+0000',
            },
            {
              id: '921455fd-849a-49c5-be17-c92eb6d9e946',
              endDate: '2020-09-30T06:14:55.185+0000',
            },
          ])
        )
      )
    );

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

    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );

    operationsStore.init();

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched')
    );

    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );

    eventListeners.online();

    expect(operationsStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(operationsStore.state.status).toEqual('fetched')
    );

    window.addEventListener = originalEventListener;
  });
});
