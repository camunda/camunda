/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {incidentsStore} from './incidents';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {waitFor} from 'modules/testing-library';
import {createInstance} from 'modules/testUtils';
import {mockIncidents} from 'modules/mocks/incidents';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';

describe('stores/incidents', () => {
  afterEach(() => {
    processInstanceDetailsStore.reset();
    incidentsStore.reset();
  });
  beforeEach(() => {
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents, {
      expectPolling: false,
    });
  });

  it('should poll for incidents if instance state is incident', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'INCIDENT'}),
    );

    vi.useFakeTimers({shouldAdvanceTime: true});
    incidentsStore.init();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual(mockIncidents),
    );

    mockFetchProcessInstanceIncidents().withSuccess(
      {
        ...mockIncidents,
        count: 2,
      },
      {expectPolling: true},
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 2,
      }),
    );

    mockFetchProcessInstanceIncidents().withSuccess(
      {
        ...mockIncidents,
        count: 3,
      },
      {expectPolling: true},
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      }),
    );

    // stop polling when instance state is no longer an incident.
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'}),
    );

    vi.runOnlyPendingTimers();
    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      }),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
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

  it('should get errorTypes', async () => {
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.errorTypes).toEqual([
      {
        id: 'NO_MORE_RETRIES',
        name: 'No more retries left',
        count: 1,
      },
    ]);
  });

  it('should get incidentsCount', async () => {
    expect(incidentsStore.incidentsCount).toBe(0);
    incidentsStore.setIncidents(mockIncidents);

    expect(incidentsStore.incidentsCount).toBe(1);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual(mockIncidents),
    );

    mockFetchProcessInstanceIncidents().withSuccess({
      ...mockIncidents,
      count: 3,
    });

    eventListeners.online();

    await waitFor(() =>
      expect(incidentsStore.state.response).toEqual({
        ...mockIncidents,
        count: 3,
      }),
    );
  });
});
