/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {incidentsStore} from './incidents.legacy';
import {currentInstanceStore} from './currentInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {createInstance} from 'modules/testUtils';
import {mockIncidentsLegacy} from 'modules/mocks/incidents';

describe('stores/incidents', () => {
  afterEach(() => {
    currentInstanceStore.reset();
    incidentsStore.reset();
  });
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/process-instances/123/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsLegacy))
      )
    );
  });

  it('should poll for incidents if instance state is incident', async () => {
    currentInstanceStore.setCurrentInstance(
      createInstance({id: '123', state: 'INCIDENT'})
    );

    jest.useFakeTimers();
    incidentsStore.init();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual(mockIncidentsLegacy)
    );

    mockServer.use(
      rest.get('/api/process-instances/123/incidents', (_, res, ctx) =>
        res.once(ctx.json({...mockIncidentsLegacy, count: 2}))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidentsLegacy,
        count: 2,
      })
    );

    mockServer.use(
      rest.get('/api/process-instances/123/incidents', (_, res, ctx) =>
        res.once(ctx.json({...mockIncidentsLegacy, count: 3}))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidentsLegacy,
        count: 3,
      })
    );

    // stop polling when instance state is no longer an incident.
    currentInstanceStore.setCurrentInstance(
      createInstance({id: '123', state: 'CANCELED'})
    );

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidentsLegacy,
        count: 3,
      })
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should reset store', async () => {
    expect(incidentsStore.state.response).toEqual(null);
    expect(incidentsStore.state.isLoaded).toBe(false);

    incidentsStore.setIncidents(mockIncidentsLegacy);
    expect(incidentsStore.state.response).toEqual(mockIncidentsLegacy);
    expect(incidentsStore.state.isLoaded).toBe(true);

    incidentsStore.reset();
    expect(incidentsStore.state.response).toEqual(null);
    expect(incidentsStore.state.isLoaded).toBe(false);
  });

  it('should get incidents', async () => {
    expect(incidentsStore.incidents).toEqual([]);
    incidentsStore.setIncidents(mockIncidentsLegacy);

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
    incidentsStore.setIncidents(mockIncidentsLegacy);

    expect(incidentsStore.flowNodes.get('Task_162x79i')).toEqual({
      flowNodeId: 'Task_162x79i',
      flowNodeName: 'Task_162x79i',
      count: 1,
    });
  });

  it('should get errorTypes', async () => {
    expect(incidentsStore.errorTypes).toEqual(new Map());
    incidentsStore.setIncidents(mockIncidentsLegacy);

    expect(incidentsStore.errorTypes.get('No more retries left')).toEqual({
      errorType: 'No more retries left',
      count: 1,
    });
  });

  it('should get incidentsCount', async () => {
    expect(incidentsStore.incidentsCount).toBe(0);
    incidentsStore.setIncidents(mockIncidentsLegacy);

    expect(incidentsStore.incidentsCount).toBe(1);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: '123',
        state: 'INCIDENT',
      })
    );
    incidentsStore.init();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual(mockIncidentsLegacy)
    );

    mockServer.use(
      rest.get('/api/process-instances/123/incidents', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockIncidentsLegacy,
            count: 3,
          })
        )
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidentsLegacy,
        count: 3,
      })
    );

    window.addEventListener = originalEventListener;
  });
});
