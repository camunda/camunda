/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {statistics} from './statistics';
import {currentInstance} from './currentInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';
import {instances} from './instances';

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
    statistics.reset();
  });

  it('should reset state', async () => {
    await statistics.fetchStatistics();
    expect(statistics.state.running).toBe(936);
    expect(statistics.state.active).toBe(725);
    expect(statistics.state.withIncidents).toBe(211);

    statistics.reset();
    expect(statistics.state.running).toBe(0);
    expect(statistics.state.active).toBe(0);
    expect(statistics.state.withIncidents).toBe(0);
  });

  it('should fetch statistics with error', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            error: 'an error occured',
          })
        )
      )
    );

    expect(statistics.state.isLoaded).toBe(false);
    expect(statistics.state.isFailed).toEqual(false);

    await statistics.fetchStatistics();
    expect(statistics.state.isLoaded).toBe(true);
    expect(statistics.state.isFailed).toBe(true);
    expect(statistics.state.running).toBe(0);
    expect(statistics.state.active).toBe(0);
    expect(statistics.state.withIncidents).toBe(0);
  });

  it('should fetch statistics with success', async () => {
    expect(statistics.state.isLoaded).toBe(false);
    expect(statistics.state.isFailed).toEqual(false);

    await statistics.fetchStatistics();
    expect(statistics.state.isLoaded).toBe(true);
    expect(statistics.state.isFailed).toBe(false);
    expect(statistics.state.running).toBe(936);
    expect(statistics.state.active).toBe(725);
    expect(statistics.state.withIncidents).toBe(211);
  });

  it('should fetch statistics on init', async () => {
    statistics.init();
    expect(statistics.state.isLoaded).toBe(false);
    expect(statistics.state.isFailed).toEqual(false);

    await waitFor(() => expect(statistics.state.isLoaded).toBe(true));
    expect(statistics.state.running).toBe(936);
    expect(statistics.state.active).toBe(725);
    expect(statistics.state.withIncidents).toBe(211);
  });

  it('should start polling when current instance exists', async () => {
    statistics.init();
    await waitFor(() => expect(statistics.state.isLoaded).toBe(true));

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

    jest.useFakeTimers();
    // should not fetch statistics when current instance does not exist
    jest.advanceTimersByTime(10000);

    expect(statistics.state.running).toBe(936);
    expect(statistics.state.active).toBe(725);
    expect(statistics.state.withIncidents).toBe(211);

    // should fetch statistics when current instance exists
    currentInstance.setCurrentInstance({id: '1'});
    jest.runOnlyPendingTimers();
    await waitFor(() => {
      expect(statistics.state.running).toBe(100);
      expect(statistics.state.active).toBe(60);
      expect(statistics.state.withIncidents).toBe(40);
    });

    jest.useRealTimers();
  });

  it('should fetch statistics depending on completed operations', async () => {
    statistics.init();
    await waitFor(() => expect(statistics.state.isLoaded).toBe(true));

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

    expect(statistics.state.running).toBe(936);
    expect(statistics.state.active).toBe(725);
    expect(statistics.state.withIncidents).toBe(211);

    instances.setInstancesWithCompletedOperations([
      {id: '1', hasActiveOperations: false},
      {id: '2', hasActiveOperations: false},
    ]);

    await waitFor(() => {
      expect(statistics.state.running).toBe(100);
      expect(statistics.state.active).toBe(60);
      expect(statistics.state.withIncidents).toBe(40);
    });
  });
});
