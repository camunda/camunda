/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filtersStore} from './filters';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowXML,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {createMemoryHistory} from 'history';
import {instancesDiagramStore} from './instancesDiagram';
import {instancesStore} from './instances';
import {workflowStatisticsStore} from './workflowStatistics';
import {instanceSelectionStore} from './instanceSelection';
import {DEFAULT_FILTER, DEFAULT_SORTING, SORT_ORDER} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

describe('stores/filters', () => {
  const fetchInstancesSpy = jest.spyOn(instancesStore, 'fetchInstances');
  beforeEach(async () => {
    const historyMock = createMemoryHistory();
    const locationMock = {pathname: '/instances'};

    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();
    filtersStore.setFilter(DEFAULT_FILTER);
    fetchInstancesSpy.mockClear();
  });
  afterEach(() => {
    filtersStore.reset();
    instancesDiagramStore.reset();
    instancesStore.reset();
    workflowStatisticsStore.reset();
    instanceSelectionStore.reset();
    jest.clearAllMocks();
  });

  it('should reset sorting if endDate filter was active and finished instances filter is not set anymore', () => {
    const sortByEndDate = {sortBy: 'endDate', sortOrder: SORT_ORDER.ASC};
    filtersStore.setSorting(sortByEndDate);
    filtersStore.setFilter({
      ...DEFAULT_FILTER,
      canceled: true,
      completed: true,
    });
    expect(filtersStore.state.sorting).toEqual(sortByEndDate);
    filtersStore.setFilter({...DEFAULT_FILTER});
    expect(filtersStore.state.sorting).toEqual(DEFAULT_SORTING);
  });

  it('should get filters payload', () => {
    expect(filtersStore.getFiltersPayload()).toEqual({
      active: true,
      incidents: true,
      running: true,
    });

    filtersStore.setFilter({
      completed: true,
      canceled: true,
      errorMessage: 'test',
    });
    expect(filtersStore.getFiltersPayload()).toEqual({
      completed: true,
      canceled: true,
      finished: true,
      errorMessage: 'test',
    });
  });

  it('should get decodedFilters', () => {
    expect(filtersStore.decodedFilters).toEqual({
      active: true,
      incidents: true,
    });
    filtersStore.setFilter({
      // @ts-expect-error
      ...filtersStore.state.filter,
      errorMessage: 'some%20error%20message',
    });
    expect(filtersStore.decodedFilters).toEqual({
      active: true,
      incidents: true,
      errorMessage: 'some error message',
    });
  });

  describe('computed values', () => {
    it('should get isNoVersionSelected', () => {
      expect(filtersStore.isNoVersionSelected).toBe(false);
      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 'all',
      });
      expect(filtersStore.isNoVersionSelected).toBe(true);
      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 1,
      });
      expect(filtersStore.isNoVersionSelected).toBe(false);
    });

    it('should get isNoWorkflowSelected', () => {
      expect(filtersStore.isNoWorkflowSelected).toBe(true);
      filtersStore.setFilter({...DEFAULT_FILTER, workflow: 'bigVarProcess'});
      expect(filtersStore.isNoWorkflowSelected).toBe(false);
    });

    it('should get workflow', () => {
      expect(filtersStore.workflow).toEqual({});
      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
      });
      expect(filtersStore.workflow).toEqual({});
      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 1,
      });
      expect(filtersStore.workflow).toEqual({
        bpmnProcessId: 'eventBasedGatewayProcess',
        id: '2251799813685911',
        name: 'Event based gateway with message start',
        version: 1,
      });
      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 'all',
      });
      expect(filtersStore.workflow).toEqual({});
    });

    it('should get workflowName', () => {
      expect(filtersStore.workflowName).toBe('Workflow');
      filtersStore.setFilter({...DEFAULT_FILTER, workflow: 'bigVarProcess'});
      expect(filtersStore.workflowName).toBe('Big variable process');
      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
      });
      expect(filtersStore.workflowName).toBe('eventBasedGatewayProcess');
      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 1,
      });
      expect(filtersStore.workflowName).toBe(
        'Event based gateway with message start'
      );
    });
  });

  it('should reset store', () => {
    const sortByEndDate = {sortBy: 'endDate', sortOrder: SORT_ORDER.ASC};
    filtersStore.setFilter({
      ...DEFAULT_FILTER,
      canceled: true,
      completed: true,
    });
    filtersStore.setSorting(sortByEndDate);
    expect(filtersStore.state.sorting).toEqual(sortByEndDate);
    expect(filtersStore.state.groupedWorkflows).not.toEqual({});
    filtersStore.reset();
    expect(filtersStore.state.sorting).toEqual(DEFAULT_SORTING);
    expect(filtersStore.state.groupedWorkflows).toEqual({});
  });
});
