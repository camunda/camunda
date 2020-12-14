/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {statisticsStore} from './statistics';
import {currentInstanceStore} from './currentInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';
import {instancesStore} from './instances';

const mockInstance = {
  id: '2251799813685625',
  workflowId: '2251799813685623',
  workflowName: 'Without Incidents Process',
  workflowVersion: 1,
  startDate: '2020-11-19T08:14:05.406+0000',
  endDate: null,
  state: 'ACTIVE',
  bpmnProcessId: 'withoutIncidentsProcess',
  hasActiveOperation: false,
  operations: [],
} as const;

describe('stores/statistics', () => {
  beforeEach(() => {
    // mock for initial fetch when statistics store is initialized
    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 936,
            active: 725,
            withIncidents: 211,
          })
        )
      )
    );
  });
  afterEach(() => {
    statisticsStore.reset();
    currentInstanceStore.reset();
  });

  it('should reset state', async () => {
    await statisticsStore.fetchStatistics();
    expect(statisticsStore.state.running).toBe(936);
    expect(statisticsStore.state.active).toBe(725);
    expect(statisticsStore.state.withIncidents).toBe(211);

    statisticsStore.reset();
    expect(statisticsStore.state.running).toBe(0);
    expect(statisticsStore.state.active).toBe(0);
    expect(statisticsStore.state.withIncidents).toBe(0);
    expect(statisticsStore.state.status).toBe('initial');
  });

  it('should fetch statistics with error', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.status(500),
          ctx.json({
            error: 'an error occurred',
          })
        )
      )
    );

    expect(statisticsStore.state.status).toBe('initial');

    await statisticsStore.fetchStatistics();

    expect(statisticsStore.state.status).toBe('error');
    expect(statisticsStore.state.running).toBe(0);
    expect(statisticsStore.state.active).toBe(0);
    expect(statisticsStore.state.withIncidents).toBe(0);
  });

  it('should fetch statistics with success', async () => {
    expect(statisticsStore.state.status).toBe('initial');

    await statisticsStore.fetchStatistics();
    expect(statisticsStore.state.status).toBe('fetched');
    expect(statisticsStore.state.running).toBe(936);
    expect(statisticsStore.state.active).toBe(725);
    expect(statisticsStore.state.withIncidents).toBe(211);
  });

  it('should fetch statistics on init', async () => {
    expect(statisticsStore.state.status).toBe('initial');
    statisticsStore.init();

    expect(statisticsStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(statisticsStore.state.running).toBe(936);
    });
    expect(statisticsStore.state.active).toBe(725);
    expect(statisticsStore.state.withIncidents).toBe(211);
  });

  it('should start polling when current instance exists', async () => {
    jest.useFakeTimers();
    statisticsStore.init();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    mockServer.use(
      // mock for when current instance is set
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 100,
            active: 60,
            withIncidents: 40,
          })
        )
      )
    );

    // should not fetch statistics when current instance does not exist
    jest.runOnlyPendingTimers();

    expect(statisticsStore.state.running).toBe(936);
    expect(statisticsStore.state.active).toBe(725);
    expect(statisticsStore.state.withIncidents).toBe(211);

    // should fetch statistics when current instance exists
    currentInstanceStore.setCurrentInstance({id: '1'});
    jest.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(100));
    expect(statisticsStore.state.active).toBe(60);
    expect(statisticsStore.state.withIncidents).toBe(40);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should fetch statistics depending on completed operations', async () => {
    statisticsStore.init();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    mockServer.use(
      // mock for when there are completed operations
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 100,
            active: 60,
            withIncidents: 40,
          })
        )
      )
    );

    expect(statisticsStore.state.running).toBe(936);
    expect(statisticsStore.state.active).toBe(725);
    expect(statisticsStore.state.withIncidents).toBe(211);

    instancesStore.setInstancesWithCompletedOperations([mockInstance]);

    await waitFor(() => expect(statisticsStore.state.running).toBe(100));
    expect(statisticsStore.state.active).toBe(60);
    expect(statisticsStore.state.withIncidents).toBe(40);
  });
});
