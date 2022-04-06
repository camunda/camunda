/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {statisticsStore} from './statistics';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {processInstancesStore} from './processInstances';
import {mockProcessXML, groupedProcessesMock} from 'modules/testUtils';
import {statistics} from 'modules/mocks/statistics';

const mockInstance = {
  id: '2251799813685625',
  processId: '2251799813685623',
  processName: 'Without Incidents Process',
  processVersion: 1,
  startDate: '2020-11-19T08:14:05.406+0000',
  endDate: null,
  state: 'ACTIVE',
  bpmnProcessId: 'withoutIncidentsProcess',
  hasActiveOperation: false,
  operations: [],
  sortValues: ['withoutIncidentsProcess', '2251799813685625'],
} as const;

describe('stores/statistics', () => {
  beforeEach(() => {
    // mock for initial fetch when statistics store is initialized
    mockServer.use(
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.json(statistics))
      )
    );
  });
  afterEach(() => {
    processInstanceDetailsStore.reset();
    statisticsStore.reset();
    processInstancesStore.reset();
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
    mockServer.use(
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
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
    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should fetch statistics on init', async () => {
    expect(statisticsStore.state.status).toBe('initial');
    statisticsStore.init();

    expect(statisticsStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(statisticsStore.state.running).toBe(1087);
    });
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);
  });

  it('should start polling on init', async () => {
    jest.useFakeTimers();
    statisticsStore.init();
    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    mockServer.use(
      // mock for when current instance is set
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.json(statistics))
      )
    );

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    jest.runOnlyPendingTimers();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1087));
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should fetch statistics depending on completed operations', async () => {
    jest.useFakeTimers();

    statisticsStore.init();

    await waitFor(() => expect(statisticsStore.state.status).toBe('fetched'));

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedProcessesMock))
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockInstance, hasActiveOperation: true}],
            totalCount: 1,
          })
        )
      )
    );
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    expect(statisticsStore.state.running).toBe(1087);
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockInstance}],
            totalCount: 1,
          })
        )
      ),
      // mock for next poll
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.json(statistics))
      ),
      // mock for when there are completed operations
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.json({...statistics, running: 1088}))
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockInstance}],
            totalCount: 2,
          })
        )
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(2)
    );

    await waitFor(() => expect(statisticsStore.state.running).toBe(1088));
    expect(statisticsStore.state.active).toBe(210);
    expect(statisticsStore.state.withIncidents).toBe(877);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    statisticsStore.init();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1087));

    mockServer.use(
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.json({...statistics, running: 1000}))
      )
    );

    eventListeners.online();

    await waitFor(() => expect(statisticsStore.state.running).toBe(1000));

    window.addEventListener = originalEventListener;
  });
});
