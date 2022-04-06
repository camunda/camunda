/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processStatisticsStore} from './processStatistics';
import {processInstancesDiagramStore} from './processInstancesDiagram';
import {mockProcessXML, mockProcessStatistics} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {processInstancesStore} from './processInstances';
import {processesStore} from './processes';

const mockInstances = [
  {
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
  } as const,
  {
    id: '2251799813685627',
    processId: '2251799813685623',
    processName: 'Without Incidents Process',
    processVersion: 1,
    startDate: '2020-11-19T08:14:05.490+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
    sortValues: ['withoutIncidentsProcess', '2251799813685627'],
  } as const,
];

describe('stores/processStatistics', () => {
  afterEach(() => {
    processStatisticsStore.reset();
    processInstancesStore.reset();
    processInstancesDiagramStore.reset();
    processesStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
  });

  it('should start and stop loading', () => {
    expect(processStatisticsStore.state.isLoading).toBe(false);
    processStatisticsStore.startLoading();
    expect(processStatisticsStore.state.isLoading).toBe(true);
    processStatisticsStore.stopLoading();
    expect(processStatisticsStore.state.isLoading).toBe(false);
  });

  it('should reset state', async () => {
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    await processStatisticsStore.fetchProcessStatistics();
    expect(processStatisticsStore.state.statistics).toEqual([
      {
        activityId: 'ServiceTask_0kt6c5i',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    processStatisticsStore.reset();
    expect(processStatisticsStore.state.statistics).toEqual([]);
  });

  it('should fetch process statistics', async () => {
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    expect(processStatisticsStore.state.isLoading).toBe(false);
    expect(processStatisticsStore.state.statistics).toEqual([]);

    processStatisticsStore.fetchProcessStatistics();
    expect(processStatisticsStore.state.isLoading).toBe(true);
    await waitFor(() =>
      expect(processStatisticsStore.state.isLoading).toBe(false)
    );

    expect(processStatisticsStore.state.statistics).toEqual([
      {
        activityId: 'ServiceTask_0kt6c5i',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);
  });

  it('should fetch process statistics depending on completed operations', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockInstances[0], hasActiveOperation: true}],
            totalCount: 1,
          })
        )
      )
    );
    processStatisticsStore.init();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    expect(processStatisticsStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({processInstances: [{...mockInstances[0]}], totalCount: 1})
        )
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    processStatisticsStore.fetchProcessStatistics();

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        mockProcessStatistics
      )
    );
  });

  it('should not fetch process statistics depending on completed operations if process and version filter does not exist', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [{...mockInstances[0], hasActiveOperation: true}],
            totalCount: 1,
          })
        )
      )
    );
    processStatisticsStore.init();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );
    expect(processStatisticsStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({processInstances: [{...mockInstances[0]}], totalCount: 2})
        )
      )
    );

    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(2)
    );

    expect(processStatisticsStore.state.statistics).toEqual([]);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    processStatisticsStore.init();
    processStatisticsStore.fetchProcessStatistics();

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        mockProcessStatistics
      )
    );

    const newStatisticsResponse = [
      ...mockProcessStatistics,
      {
        activityId: 'ServiceTask_0kt6c5i_new',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ];
    mockServer.use(
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(newStatisticsResponse))
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        newStatisticsResponse
      )
    );

    window.addEventListener = originalEventListener;
  });
});
