/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instances} from './instances';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';
import {filters} from './filters';
import {DEFAULT_FILTER, DEFAULT_SORTING} from 'modules/constants';
import {createMemoryHistory} from 'history';
import {groupedWorkflowsMock} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

const mockWorkflowInstances = {
  workflowInstances: [
    {
      id: 'instance_id_1',
      state: 'ACTIVE',
    },
    {
      id: 'instance_id_2',
      state: 'ACTIVE',
    },
  ],
  totalCount: 100,
};

describe('stores/instances', () => {
  const fetchInstancesSpy = jest.spyOn(instances, 'fetchInstances');
  const historyMock = createMemoryHistory();
  const locationMock = {pathname: '/instances'};

  beforeEach(async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );
    filters.setUrlParameters(historyMock, locationMock);
    await filters.init();
  });
  afterEach(() => {
    clearStateLocally();
    instances.reset();
    filters.reset();
  });

  it('should return null by default', () => {
    expect(instances.state.filteredInstancesCount).toBe(null);
  });

  // This test is skipped, because setting the local storage inside
  // the test has no effect. See https://jira.camunda.com/browse/OPE-1004
  it.skip('should return state from local storage', () => {
    instances.reset();
    storeStateLocally({filteredInstancesCount: 312});

    expect(instances.state.filteredInstancesCount).toBe(312);
  });

  it('should return store state', () => {
    instances.setInstances({filteredInstancesCount: 654});

    expect(instances.state.filteredInstancesCount).toBe(654);
  });

  it('should return store state when both is set', () => {
    storeStateLocally({filteredInstancesCount: 101});
    instances.setInstances({filteredInstancesCount: 202});

    expect(instances.state.filteredInstancesCount).toBe(202);
  });

  it('should start and stop loading', () => {
    expect(instances.state.isLoading).toBe(false);
    instances.startLoading();
    expect(instances.state.isLoading).toBe(true);
    instances.stopLoading();
    expect(instances.state.isLoading).toBe(false);
  });

  it('should complete initial load', () => {
    expect(instances.state.isInitialLoadComplete).toBe(false);
    instances.completeInitialLoad();
    expect(instances.state.isInitialLoadComplete).toBe(true);
  });

  it('should fetch instances', async () => {
    expect(instances.state.isLoading).toEqual(false);
    expect(instances.state.isInitialLoadComplete).toEqual(false);

    const instancesRequest = instances.fetchInstances({});

    expect(instances.state.isLoading).toEqual(true);
    expect(instances.state.isInitialLoadComplete).toEqual(false);

    await instancesRequest;

    expect(instances.state.isLoading).toEqual(false);
    expect(instances.state.isInitialLoadComplete).toEqual(true);

    expect(instances.state.filteredInstancesCount).toBe(100);
    expect(instances.state.workflowInstances).toEqual(
      mockWorkflowInstances.workflowInstances
    );
  });

  it('should refresh instances', async () => {
    expect(instances.state.isLoading).toEqual(false);

    const instancesRequest = instances.refreshInstances({});

    expect(instances.state.isLoading).toEqual(false);

    await instancesRequest;

    expect(instances.state.isLoading).toEqual(false);

    expect(instances.state.filteredInstancesCount).toBe(
      mockWorkflowInstances.totalCount
    );
    expect(instances.state.workflowInstances).toEqual(
      mockWorkflowInstances.workflowInstances
    );
  });

  it('should reset store (keep the filteredInstancesCount value)', async () => {
    await instances.fetchInstances({});

    expect(instances.state.workflowInstances).toEqual(
      mockWorkflowInstances.workflowInstances
    );
    const filteredInstancesCount = instances.state.filteredInstancesCount;
    instances.reset();
    expect(instances.state).toEqual({
      filteredInstancesCount: filteredInstancesCount,
      workflowInstances: [],
      isLoading: false,
      isInitialLoadComplete: false,
    });
  });

  it('should get workflow instance ids', async () => {
    await instances.fetchInstances({});

    expect(instances.workflowInstanceIds).toEqual([
      'instance_id_1',
      'instance_id_2',
    ]);
  });

  describe('fetch instances autorun', () => {
    beforeEach(() => {
      instances.init();
      fetchInstancesSpy.mockReset();
    });
    it('should fetch instances every time sorting changes', () => {
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(0);
      expect(filters.state.sorting).toEqual(DEFAULT_SORTING);
      filters.setSorting({...DEFAULT_SORTING, sortBy: 'instanceId'});
      expect(filters.state.sorting).toEqual({
        ...DEFAULT_SORTING,
        sortBy: 'instanceId',
      });

      expect(fetchInstancesSpy).toHaveBeenCalledTimes(1);

      filters.setSorting(DEFAULT_SORTING);
      expect(filters.state.sorting).toEqual(DEFAULT_SORTING);

      expect(fetchInstancesSpy).toHaveBeenCalledTimes(2);
    });

    it('should fetch instances every time page or entries per page changes', () => {
      filters.setEntriesPerPage(10);

      expect(filters.state.page).toEqual(1);
      expect(filters.state.prevEntriesPerPage).toEqual(0);
      expect(filters.state.entriesPerPage).toEqual(10);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(0);

      filters.setPage(2);

      expect(filters.state.page).toEqual(2);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(1);

      filters.setEntriesPerPage(5);

      expect(filters.state.page).toEqual(2);
      expect(filters.state.prevEntriesPerPage).toEqual(10);
      expect(filters.state.entriesPerPage).toEqual(5);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(2);

      filters.setEntriesPerPage(10);

      expect(filters.state.page).toEqual(2);
      expect(filters.state.prevEntriesPerPage).toEqual(5);
      expect(filters.state.entriesPerPage).toEqual(10);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(3);
    });

    it('should fetch instances every time filter changes', () => {
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(0);

      filters.setFilter({...DEFAULT_FILTER, startDate: '2020-01-01 12:30:00'});
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(1);

      filters.setFilter({...DEFAULT_FILTER, startDate: '2022-01-01 12:30:00'});
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(2);
    });
  });
});
