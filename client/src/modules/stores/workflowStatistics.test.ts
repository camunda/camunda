/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {workflowStatisticsStore} from './workflowStatistics';
import {instancesDiagramStore} from './instancesDiagram';
import {createMemoryHistory} from 'history';
import {filtersStore} from './filters';
import {
  groupedWorkflowsMock,
  mockWorkflowXML,
  mockWorkflowStatistics,
} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';
import {instancesStore} from './instances';

const mockInstances = [
  {
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
  } as const,
  {
    id: '2251799813685627',
    workflowId: '2251799813685623',
    workflowName: 'Without Incidents Process',
    workflowVersion: 1,
    startDate: '2020-11-19T08:14:05.490+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
  } as const,
];

describe('stores/workflowStatistics', () => {
  afterEach(() => {
    workflowStatisticsStore.reset();
    filtersStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      )
    );
  });

  it('should start and stop loading', () => {
    expect(workflowStatisticsStore.state.isLoading).toBe(false);
    workflowStatisticsStore.startLoading();
    expect(workflowStatisticsStore.state.isLoading).toBe(true);
    workflowStatisticsStore.stopLoading();
    expect(workflowStatisticsStore.state.isLoading).toBe(false);
  });

  it('should reset state', async () => {
    mockServer.use(
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    await workflowStatisticsStore.fetchWorkflowStatistics();
    expect(workflowStatisticsStore.state.statistics).toEqual([
      {
        activityId: 'ServiceTask_0kt6c5i',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    workflowStatisticsStore.reset();
    expect(workflowStatisticsStore.state.statistics).toEqual([]);
  });

  it('should fetch workflow statistics', async () => {
    mockServer.use(
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    expect(workflowStatisticsStore.state.isLoading).toBe(false);
    expect(workflowStatisticsStore.state.statistics).toEqual([]);

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    workflowStatisticsStore.fetchWorkflowStatistics();
    expect(workflowStatisticsStore.state.isLoading).toBe(true);
    await waitFor(() =>
      expect(workflowStatisticsStore.state.isLoading).toBe(false)
    );

    expect(workflowStatisticsStore.state.statistics).toEqual([
      {
        activityId: 'ServiceTask_0kt6c5i',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);
  });

  it('should fetch and update workflow statistics every time diagram changes', async () => {
    filtersStore.setUrlParameters(createMemoryHistory(), {
      pathname: '/instances',
    });
    await filtersStore.init();
    workflowStatisticsStore.init();

    expect(workflowStatisticsStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    await instancesDiagramStore.fetchWorkflowXml('1');
    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual(
        mockWorkflowStatistics
      )
    );

    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              activityId: 'ServiceTask_0kt6c5i',
              active: 2,
              canceled: 1,
              incidents: 1,
              completed: 1,
            },
          ])
        )
      )
    );

    await instancesDiagramStore.fetchWorkflowXml('1');
    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual([
        {
          activityId: 'ServiceTask_0kt6c5i',
          active: 2,
          canceled: 1,
          incidents: 1,
          completed: 1,
        },
      ])
    );
  });

  it('should fetch/reset workflow statistics depending on filters', async () => {
    filtersStore.setUrlParameters(createMemoryHistory(), {
      pathname: '/instances',
    });
    await filtersStore.init();
    workflowStatisticsStore.init();

    // should not fetch statistics on workflow or version change (handled by diagram reaction)
    filtersStore.setFilter({workflow: 'bigVarProcess', version: '1'});
    expect(workflowStatisticsStore.state.statistics).toEqual([]);

    // should fetch when any other filter changes
    mockServer.use(
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );
    filtersStore.setFilter({
      // @ts-expect-error
      ...filtersStore.state.filter,
      errorMessage: 'bigVarProcess',
    });

    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual(
        mockWorkflowStatistics
      )
    );

    // should reset statistics when workflow and version filters no longer exists
    filtersStore.setFilter({
      errorMessage: 'bigVarProcess',
    });

    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual([])
    );

    // should not react if filter is the same as previous
    filtersStore.setFilter({
      errorMessage: 'bigVarProcess',
    });

    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual([])
    );
  });

  it('should fetch workflow statistics depending on completed operations', async () => {
    filtersStore.setUrlParameters(createMemoryHistory(), {
      pathname: '/instances',
    });
    await filtersStore.init();
    workflowStatisticsStore.init();

    filtersStore.setFilter({workflow: 'bigVarProcess', version: '1'});

    expect(workflowStatisticsStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );
    instancesStore.setInstancesWithCompletedOperations(mockInstances);

    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual(
        mockWorkflowStatistics
      )
    );
  });

  it('should not fetch workflow statistics depending on completed operations if workflow and version filter does not exist', async () => {
    filtersStore.setUrlParameters(createMemoryHistory(), {
      pathname: '/instances',
    });
    await filtersStore.init();
    workflowStatisticsStore.init();

    expect(workflowStatisticsStore.state.statistics).toEqual([]);

    instancesStore.setInstancesWithCompletedOperations(mockInstances);

    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual([])
    );
  });
});
