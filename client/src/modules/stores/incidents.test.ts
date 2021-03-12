/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {incidentsStore} from './incidents';
import {currentInstanceStore} from './currentInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';

describe('stores/incidents', () => {
  const mockIncidents = {
    count: 1,
    incidents: [
      {
        id: '2251799813700301',
        errorType: 'No more retries left',
        errorMessage: 'Cannot connect to server delivery05',
        flowNodeId: 'Task_162x79i',
        flowNodeInstanceId: '2251799813699889',
        jobId: '2251799813699901',
        creationTime: '2020-10-08T09:18:58.258+0000',
        hasActiveOperation: false,
        lastOperation: null,
      },
    ],
    errorTypes: [
      {
        errorType: 'No more retries left',
        count: 1,
      },
    ],
    flowNodes: [
      {
        flowNodeId: 'Task_162x79i',
        count: 1,
      },
    ],
  };
  afterEach(() => {
    currentInstanceStore.reset();
    incidentsStore.reset();
  });
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/workflow-instances/123/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      )
    );
  });

  it('should poll for incidents if instance state is incident', async () => {
    currentInstanceStore.setCurrentInstance({id: 123, state: 'INCIDENT'});

    jest.useFakeTimers();
    incidentsStore.init();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual(mockIncidents)
    );

    mockServer.use(
      rest.get('/api/workflow-instances/123/incidents', (_, res, ctx) =>
        res.once(ctx.json({...mockIncidents, count: 2}))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 2,
      })
    );

    mockServer.use(
      rest.get('/api/workflow-instances/123/incidents', (_, res, ctx) =>
        res.once(ctx.json({...mockIncidents, count: 3}))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      })
    );

    // stop polling when instance state is no longer an incident.
    currentInstanceStore.setCurrentInstance({id: 123, state: 'CANCELED'});

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      })
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should reset store', async () => {
    expect(incidentsStore.state.response).toEqual(null);
    expect(incidentsStore.state.isLoaded).toBe(false);

    incidentsStore.setIncidents(mockIncidents);
    expect(incidentsStore.state.response).toEqual(mockIncidents);
    expect(incidentsStore.state.isLoaded).toBe(true);

    incidentsStore.reset();
    expect(incidentsStore.state.response).toEqual(null);
    expect(incidentsStore.state.isLoaded).toBe(false);
  });

  it('should get incidents', async () => {
    expect(incidentsStore.incidents).toEqual([]);
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.incidents).toEqual([
      {
        creationTime: '2020-10-08T09:18:58.258+0000',
        errorMessage: 'Cannot connect to server delivery05',
        errorType: 'No more retries left',
        flowNodeId: 'Task_162x79i',
        flowNodeInstanceId: '2251799813699889',
        flowNodeName: 'Task_162x79i',
        hasActiveOperation: false,
        id: '2251799813700301',
        jobId: '2251799813699901',
        lastOperation: null,
      },
    ]);
  });

  it('should get flowNodes', async () => {
    expect(incidentsStore.flowNodes).toEqual(new Map());
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.flowNodes.get('Task_162x79i')).toEqual({
      flowNodeId: 'Task_162x79i',
      flowNodeName: 'Task_162x79i',
      count: 1,
    });
  });

  it('should get errorTypes', async () => {
    expect(incidentsStore.errorTypes).toEqual(new Map());
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.errorTypes.get('No more retries left')).toEqual({
      errorType: 'No more retries left',
      count: 1,
    });
  });

  it('should get incidentsCount', async () => {
    expect(incidentsStore.incidentsCount).toBe(0);
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.incidentsCount).toBe(1);
  });
});
