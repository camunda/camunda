/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {statisticsStore} from './statistics';
import {waitFor} from 'modules/testing-library';
import {processInstancesStore} from './processInstances';
import {createInstance, mockProcessDefinitions} from 'modules/testUtils';
import {statistics} from 'modules/mocks/statistics';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchProcessCoreStatistics} from 'modules/mocks/api/processInstances/fetchProcessCoreStatistics';
import {mockServer} from 'modules/mock-server/node';
import {http, HttpResponse} from 'msw';
import {checkPollingHeader} from 'modules/mocks/api/mockRequest';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

const mockInstance = createInstance({id: '2251799813685625'});

describe('stores/statistics', () => {
  beforeEach(() => {
    // mock for initial fetch when statistics store is initialized
    mockFetchProcessCoreStatistics().withSuccess(statistics, {
      expectPolling: false,
    });
  });

  afterEach(() => {
    statisticsStore.reset();
    processInstancesStore.reset();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should reset state', async () => {
    await statisticsStore.fetchStatistics();
    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    statisticsStore.reset();
    expect(statisticsStore.state.running).toBe(0);
    expect(statisticsStore.state.active).toBe(0);
    expect(statisticsStore.state.withIncidents).toBe(0);
    expect(statisticsStore.state.status).toBe('initial');
  });

  it('should fetch statistics with error', async () => {
    mockFetchProcessCoreStatistics().withServerError();

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
    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should fetch statistics', async () => {
    expect(statisticsStore.state.status).toBe('initial');
    statisticsStore.fetchStatistics();

    expect(statisticsStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(statisticsStore.state.running).toBe(1087);
    });
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should start polling on init', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics, {
      expectPolling: true,
    });

    vi.useFakeTimers({shouldAdvanceTime: true});

    statisticsStore.init();
    vi.runOnlyPendingTimers();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    mockFetchProcessCoreStatistics().withSuccess(
      {
        running: 1088,
        active: 211,
        withIncidents: 878,
      },
      {expectPolling: true},
    );
    vi.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1088));
    expect(statisticsStore.state.active).toBe(211);
    expect(statisticsStore.state.withIncidents).toBe(878);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should fetch statistics depending on completed operations', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics, {
      expectPolling: true,
    });
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

    // mock for refresh all instances
    mockFetchProcessInstances().withSuccess({
      processInstances: [{...mockInstance, hasActiveOperation: true}],
      totalCount: 1,
    });

    // mock for refresh running process instances count
    mockFetchProcessInstances().withSuccess({
      processInstances: [{...mockInstance, hasActiveOperation: true}],
      totalCount: 1,
    });

    statisticsStore.init();

    vi.runOnlyPendingTimers();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    mockServer.use(
      http.post(
        '/api/process-instances',
        ({request}) => {
          checkPollingHeader({req: request, expectPolling: true});
          return HttpResponse.json({
            processInstances: [{...mockInstance}],
            totalCount: 1,
          });
        },
        {once: true},
      ),
      http.post(
        '/api/process-instances',
        ({request}) => {
          checkPollingHeader({req: request, expectPolling: true});
          return HttpResponse.json({
            processInstances: [{...mockInstance}],
            totalCount: 2,
          });
        },
        {once: true},
      ),
      http.post(
        '/api/process-instances',
        ({request}) => {
          checkPollingHeader({req: request, expectPolling: true});
          return HttpResponse.json({
            processInstances: [{...mockInstance}],
            totalCount: 2,
          });
        },
        {once: true},
      ),
      http.get(
        '/api/process-instances/core-statistics',
        ({request}) => {
          checkPollingHeader({req: request, expectPolling: true});
          return HttpResponse.json({
            ...statistics,
          });
        },
        {once: true},
      ),
      http.get(
        '/api/process-instances/core-statistics',
        ({request}) => {
          checkPollingHeader({req: request, expectPolling: true});
          return HttpResponse.json({
            ...statistics,
            running: 1088,
          });
        },
        {once: true},
      ),
      http.get(
        '/api/process-instances/core-statistics',
        ({request}) => {
          checkPollingHeader({req: request, expectPolling: true});
          return HttpResponse.json({
            ...statistics,
            running: 1088,
          });
        },
        {once: true},
      ),
    );

    vi.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1088));
    await waitFor(() =>
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(2),
    );
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    statisticsStore.fetchStatistics();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1087));

    mockFetchProcessCoreStatistics().withSuccess({
      ...statistics,
      running: 1000,
    });

    eventListeners.online();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1000));
  });
});
