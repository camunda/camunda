/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filters} from './filters';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowXML,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {createMemoryHistory} from 'history';
import {instancesDiagram} from './instancesDiagram';
import {instances} from './instances';
import {workflowStatistics} from './workflowStatistics';
import {instanceSelection} from './instanceSelection';
import {DEFAULT_FILTER, DEFAULT_SORTING, SORT_ORDER} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

describe('stores/filters', () => {
  const fetchInstancesSpy = jest.spyOn(instances, 'fetchInstances');
  beforeEach(async () => {
    const historyMock = createMemoryHistory();
    const locationMock = {pathname: '/instances'};

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
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

    filters.setUrlParameters(historyMock, locationMock);
    await filters.init();
    filters.setFilter(DEFAULT_FILTER);
    fetchInstancesSpy.mockClear();
  });
  afterEach(() => {
    filters.reset();
    instancesDiagram.reset();
    instances.reset();
    workflowStatistics.reset();
    instanceSelection.reset();
    jest.clearAllMocks();
  });

  describe('filter observer', () => {
    it('should go to first page every time filter changes', () => {
      filters.setPage(3);
      expect(filters.state.page).toBe(3);
      filters.setFilter({
        ...filters.state.filter,
        errorMessage: 'test',
      });
      expect(filters.state.page).toBe(1);
    });
  });

  it('should reset sorting if endDate filter was active and finished instances filter is not set anymore', () => {
    const sortByEndDate = {sortBy: 'endDate', sortOrder: SORT_ORDER.ASC};
    filters.setSorting(sortByEndDate);
    filters.setFilter({...DEFAULT_FILTER, canceled: true, completed: true});
    expect(filters.state.sorting).toEqual(sortByEndDate);
    filters.setFilter({...DEFAULT_FILTER});
    expect(filters.state.sorting).toEqual(DEFAULT_SORTING);
  });

  it('should get filters payload', () => {
    expect(filters.getFiltersPayload()).toEqual({
      active: true,
      incidents: true,
      running: true,
    });

    filters.setFilter({completed: true, canceled: true, errorMessage: 'test'});
    expect(filters.getFiltersPayload()).toEqual({
      completed: true,
      canceled: true,
      finished: true,
      errorMessage: 'test',
    });
  });

  it('should get decodedFilters', () => {
    expect(filters.decodedFilters).toEqual({active: true, incidents: true});
    filters.setFilter({
      ...filters.state.filter,
      errorMessage: 'some%20error%20message',
    });
    expect(filters.decodedFilters).toEqual({
      active: true,
      incidents: true,
      errorMessage: 'some error message',
    });
  });

  describe('computed values', () => {
    it('should get firstElement', () => {
      expect(filters.firstElement).toBe(0);
      filters.setEntriesPerPage(10);
      expect(filters.firstElement).toBe(0);
      filters.setPage(2);
      expect(filters.firstElement).toBe(10);
    });

    it('should get isNoVersionSelected', () => {
      expect(filters.isNoVersionSelected).toBe(false);
      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 'all',
      });
      expect(filters.isNoVersionSelected).toBe(true);
      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 1,
      });
      expect(filters.isNoVersionSelected).toBe(false);
    });

    it('should get isNoWorkflowSelected', () => {
      expect(filters.isNoWorkflowSelected).toBe(true);
      filters.setFilter({...DEFAULT_FILTER, workflow: 'bigVarProcess'});
      expect(filters.isNoWorkflowSelected).toBe(false);
    });

    it('should get workflow', () => {
      expect(filters.workflow).toEqual({});
      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
      });
      expect(filters.workflow).toEqual({});
      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 1,
      });
      expect(filters.workflow).toEqual({
        bpmnProcessId: 'eventBasedGatewayProcess',
        id: '2251799813685911',
        name: 'Event based gateway with message start',
        version: 1,
      });
      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 'all',
      });
      expect(filters.workflow).toEqual({});
    });

    it('should get workflowName', () => {
      expect(filters.workflowName).toBe('Workflow');
      filters.setFilter({...DEFAULT_FILTER, workflow: 'bigVarProcess'});
      expect(filters.workflowName).toBe('Big variable process');
      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
      });
      expect(filters.workflowName).toBe('eventBasedGatewayProcess');
      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'eventBasedGatewayProcess',
        version: 1,
      });
      expect(filters.workflowName).toBe(
        'Event based gateway with message start'
      );
    });
  });

  it('should reset store', () => {
    const sortByEndDate = {sortBy: 'endDate', sortOrder: SORT_ORDER.ASC};
    filters.setFilter({...DEFAULT_FILTER, canceled: true, completed: true});
    filters.setEntriesPerPage(10);
    filters.setPage(3);
    filters.setSorting(sortByEndDate);
    expect(filters.state.page).toBe(3);
    expect(filters.state.entriesPerPage).toBe(10);
    expect(filters.state.prevEntriesPerPage).toBe(0);
    expect(filters.state.sorting).toEqual(sortByEndDate);
    expect(filters.state.groupedWorkflows).not.toEqual({});
    filters.reset();
    expect(filters.state.page).toBe(1);
    expect(filters.state.entriesPerPage).toBe(0);
    expect(filters.state.prevEntriesPerPage).toBe(0);
    expect(filters.state.sorting).toEqual(DEFAULT_SORTING);
    expect(filters.state.groupedWorkflows).toEqual({});
  });
});
