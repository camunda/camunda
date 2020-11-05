/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {operationsStore} from './operations';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';
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
    expect(operationsStore.state.isInitialLoadComplete).toBe(true);
    operationsStore.reset();
    expect(operationsStore.state.operations).toEqual([]);
    expect(operationsStore.state.isInitialLoadComplete).toEqual(false);
  });

  it('should increase page if operations are requested with searchAfter parameter', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );

    expect(operationsStore.state.page).toBe(1);
    await operationsStore.fetchOperations('20');

    expect(operationsStore.state.page).toBe(2);
  });

  it('should prepend operations when an operation is applied', async () => {
    const newOperation = {
      id: 'c6cde799-69bc-4dd5-9f98-3f931aa2c922',
      name: null,
      type: 'CANCEL_WORKFLOW_INSTANCE',
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
        '/api/workflow-instances/:instanceId/operation',
        (_, res, ctx) => res.once(ctx.json(newOperation))
      )
    );

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    await operationsStore.applyOperation(1, {});
    expect(operationsStore.state.operations).toEqual([
      newOperation,
      ...operations,
    ]);
  });

  it('should prepend operations when an operation is applied', async () => {
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
      rest.post('/api/workflow-instances/batch-operation', (_, res, ctx) =>
        res.once(ctx.json(newOperation))
      )
    );

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    await operationsStore.applyBatchOperation('CANCEL_WORKFLOW_INSTANCE', {});
    expect(operationsStore.state.operations).toEqual([
      newOperation,
      ...operations,
    ]);
  });

  it('should increase page', () => {
    expect(operationsStore.state.page).toBe(1);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(2);

    operationsStore.increasePage();
    expect(operationsStore.state.page).toBe(3);
  });

  it('should complete initial load', () => {
    expect(operationsStore.state.isInitialLoadComplete).toBe(false);
    operationsStore.completeInitialLoad();
    expect(operationsStore.state.isInitialLoadComplete).toBe(true);
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
    await waitFor(() =>
      expect(operationsStore.state.isInitialLoadComplete).toBe(true)
    );

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

    await operationsStore.fetchOperations();
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

    jest.useRealTimers();
  });
});
