/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {workflowStatisticsStore} from './workflowStatistics';
import {instancesDiagramStore} from './instancesDiagram';
import {mockWorkflowXML, mockWorkflowStatistics} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {instancesStore} from './instances';
import {workflowsStore} from './workflows';

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
    sortValues: ['withoutIncidentsProcess', '2251799813685625'],
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
    sortValues: ['withoutIncidentsProcess', '2251799813685627'],
  } as const,
];

describe('stores/workflowStatistics', () => {
  afterEach(() => {
    workflowStatisticsStore.reset();
    instancesStore.reset();
    instancesDiagramStore.reset();
    workflowsStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
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

  it('should fetch workflow statistics depending on completed operations', async () => {
    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            workflowInstances: [
              {...mockInstances[0], hasActiveOperation: true},
            ],
            totalCount: 1,
          })
        )
      )
    );
    workflowStatisticsStore.init();
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    expect(workflowStatisticsStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(
          ctx.json({workflowInstances: [{...mockInstances[0]}], totalCount: 1})
        )
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    workflowStatisticsStore.fetchWorkflowStatistics();

    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual(
        mockWorkflowStatistics
      )
    );
  });

  it('should not fetch workflow statistics depending on completed operations if workflow and version filter does not exist', async () => {
    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            workflowInstances: [
              {...mockInstances[0], hasActiveOperation: true},
            ],
            totalCount: 1,
          })
        )
      )
    );
    workflowStatisticsStore.init();
    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
    expect(workflowStatisticsStore.state.statistics).toEqual([]);

    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(
          ctx.json({workflowInstances: [{...mockInstances[0]}], totalCount: 2})
        )
      )
    );

    instancesStore.fetchInstancesFromFilters();

    await waitFor(() =>
      expect(instancesStore.state.filteredInstancesCount).toBe(2)
    );

    expect(workflowStatisticsStore.state.statistics).toEqual([]);
  });
});
