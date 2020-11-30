/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instancesStore} from './instances';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';
import {filtersStore} from './filters';
import {DEFAULT_FILTER, DEFAULT_SORTING} from 'modules/constants';
import {createMemoryHistory} from 'history';
import {groupedWorkflowsMock} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';

const instance: InstanceEntity = {
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
};

const instanceWithActiveOperation: InstanceEntity = {
  id: '2251799813685627',
  workflowId: '2251799813685623',
  workflowName: 'Without Incidents Process',
  workflowVersion: 1,
  startDate: '2020-11-19T08:14:05.490+0000',
  endDate: null,
  state: 'ACTIVE',
  bpmnProcessId: 'withoutIncidentsProcess',
  hasActiveOperation: true,
  operations: [],
};

const mockInstances = [instance, instanceWithActiveOperation];

const mockWorkflowInstances = {
  workflowInstances: mockInstances,
  totalCount: 100,
};

describe('stores/instances', () => {
  const fetchInstancesSpy = jest.spyOn(instancesStore, 'fetchInstances');
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
    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();
  });
  afterEach(() => {
    clearStateLocally();
    instancesStore.reset();
    filtersStore.reset();
  });

  it('should return null by default', () => {
    expect(instancesStore.state.filteredInstancesCount).toBe(null);
  });

  // This test is skipped, because setting the local storage inside
  // the test has no effect. See https://jira.camunda.com/browse/OPE-1004
  it.skip('should return state from local storage', () => {
    instancesStore.reset();
    storeStateLocally({filteredInstancesCount: 312});

    expect(instancesStore.state.filteredInstancesCount).toBe(312);
  });

  it('should return store state', () => {
    instancesStore.setInstances({
      filteredInstancesCount: 654,
      workflowInstances: [],
    });

    expect(instancesStore.state.filteredInstancesCount).toBe(654);
  });

  it('should return store state when both is set', () => {
    storeStateLocally({filteredInstancesCount: 101});
    instancesStore.setInstances({
      filteredInstancesCount: 202,
      workflowInstances: [],
    });

    expect(instancesStore.state.filteredInstancesCount).toBe(202);
  });

  it('should fetch instances', async () => {
    expect(instancesStore.state.status).toBe('initial');

    instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });

    expect(instancesStore.state.status).toBe('first-fetch');

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    expect(instancesStore.state.filteredInstancesCount).toBe(100);
    expect(instancesStore.state.workflowInstances).toEqual(
      mockWorkflowInstances.workflowInstances
    );
  });

  it('should refresh instances', async () => {
    expect(instancesStore.state.status).toBe('initial');

    instancesStore.refreshInstances({
      firstResult: 0,
      maxResults: 50,
    });

    expect(instancesStore.state.status).toBe('initial');

    await waitFor(() =>
      expect(instancesStore.state.workflowInstances).toEqual(
        mockWorkflowInstances.workflowInstances
      )
    );
    expect(instancesStore.state.filteredInstancesCount).toBe(
      mockWorkflowInstances.totalCount
    );
  });

  it('should reset store (keep the filteredInstancesCount value)', async () => {
    await instancesStore.fetchInstances({firstResult: 0, maxResults: 50});

    expect(instancesStore.state.workflowInstances).toEqual(
      mockWorkflowInstances.workflowInstances
    );
    const filteredInstancesCount = instancesStore.state.filteredInstancesCount;
    instancesStore.reset();
    expect(instancesStore.state).toEqual({
      filteredInstancesCount: filteredInstancesCount,
      workflowInstances: [],
      status: 'initial',
      instancesWithActiveOperations: [],
      instancesWithCompletedOperations: [],
    });
  });

  it('should get visible ids in list panel', async () => {
    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });

    expect(instancesStore.visibleIdsInListPanel).toEqual(
      mockInstances.map(({id}) => id)
    );
  });

  it('should get areWorkflowInstancesEmpty', async () => {
    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });
    expect(instancesStore.areWorkflowInstancesEmpty).toBe(false);

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(ctx.json({workflowInstances: [], totalCount: 0}))
      )
    );

    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });
    expect(instancesStore.areWorkflowInstancesEmpty).toBe(true);
  });

  it('should add instances with active operations', async () => {
    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });

    instancesStore.addInstancesWithActiveOperations({ids: [instance.id]});

    expect(instancesStore.state.instancesWithActiveOperations).toHaveLength(
      mockInstances.length
    );
    expect(instancesStore.state.instancesWithActiveOperations).toEqual(
      expect.arrayContaining(mockInstances.map(({id}) => id))
    );

    instancesStore.addInstancesWithActiveOperations({
      ids: ['non_existing_instance_id'],
    });
    expect(instancesStore.state.instancesWithActiveOperations).toHaveLength(
      mockInstances.length
    );
    expect(instancesStore.state.instancesWithActiveOperations).toEqual(
      expect.arrayContaining(mockInstances.map(({id}) => id))
    );

    instancesStore.addInstancesWithActiveOperations({
      ids: [],
      shouldPollAllVisibleIds: true,
    });

    expect(instancesStore.state.instancesWithActiveOperations).toHaveLength(
      mockInstances.length
    );
    expect(instancesStore.state.instancesWithActiveOperations).toEqual(
      expect.arrayContaining(mockInstances.map(({id}) => id))
    );

    instancesStore.addInstancesWithActiveOperations({
      ids: [instance.id],
      shouldPollAllVisibleIds: true,
    });
    expect(instancesStore.state.instancesWithActiveOperations).toEqual([
      instanceWithActiveOperation.id,
    ]);

    instancesStore.addInstancesWithActiveOperations({
      ids: ['non_existing_instance_id'],
      shouldPollAllVisibleIds: true,
    });

    expect(instancesStore.state.instancesWithActiveOperations).toHaveLength(
      mockInstances.length
    );
    expect(instancesStore.state.instancesWithActiveOperations).toEqual(
      expect.arrayContaining(mockInstances.map(({id}) => id))
    );
  });

  it('should set instances with active operations', () => {
    expect(instancesStore.state.instancesWithActiveOperations).toEqual([]);
    instancesStore.setInstancesWithActiveOperations(mockInstances);
    expect(instancesStore.state.instancesWithActiveOperations).toEqual([
      instanceWithActiveOperation.id,
    ]);
  });

  it('should set/reset instances with completed operations', () => {
    expect(instancesStore.state.instancesWithCompletedOperations).toEqual([]);
    instancesStore.setInstancesWithCompletedOperations(mockInstances);
    expect(instancesStore.state.instancesWithCompletedOperations).toEqual([
      instance.id,
    ]);
    instancesStore.resetInstancesWithCompletedOperations();
    expect(instancesStore.state.instancesWithCompletedOperations).toEqual([]);
  });

  it('should refresh instances and reset instances with completed operations every time there is an instance with completed operation', async () => {
    instancesStore.init();

    await waitFor(() =>
      expect(instancesStore.state.workflowInstances.length).toBe(2)
    );
    expect(instancesStore.state.instancesWithCompletedOperations).toEqual([]);
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [
                {
                  id: 'instance_id_1',
                  state: 'ACTIVE',
                },
                {
                  id: 'instance_id_2',
                  state: 'ACTIVE',
                },
                {
                  id: 'instance_id_3',
                  state: 'ACTIVE',
                },
              ],
              totalCount: 100,
            })
          )
      )
    );
    instancesStore.setInstancesWithCompletedOperations(mockInstances);
    await waitFor(() =>
      expect(instancesStore.state.workflowInstances.length).toBe(3)
    );

    expect(instancesStore.state.instancesWithCompletedOperations).toEqual([]);
  });

  it('should poll instances by id when there are instances with active operations', async () => {
    instancesStore.init();

    await waitFor(() =>
      expect(instancesStore.state.workflowInstances.length).toBe(2)
    );
    expect(instancesStore.state.instancesWithActiveOperations).toEqual([
      instanceWithActiveOperation.id,
    ]);
    jest.useFakeTimers();
    mockServer.use(
      // mock for fetching instances when there is an active operation
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=2',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [
                {id: 'instance_id_1', hasActiveOperation: true},
                {id: 'instance_id_2', hasActiveOperation: false},
              ],
              totalCount: 100,
            })
          )
      ),
      // mock for refreshing instances when an instance operation is completed
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=50',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [
                {id: 'instance_id_1', hasActiveOperation: true},
                {id: 'instance_id_2', hasActiveOperation: false},
              ],
              totalCount: 100,
            })
          )
      ),
      // mock for fetching instances when there is an active operation
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=1',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [
                {id: 'instance_id_1', hasActiveOperation: false},
              ],
              totalCount: 100,
            })
          )
      ),
      // mock for refreshing instances when an instance operation is completed
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=50',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      )
    );
    instancesStore.setInstancesWithActiveOperations(mockInstances);
    jest.runOnlyPendingTimers();
    await waitFor(() => {
      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        instanceWithActiveOperation.id,
      ]);
    });
    jest.runOnlyPendingTimers();
    await waitFor(() => {
      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        instanceWithActiveOperation.id,
      ]);
      expect(instancesStore.state.workflowInstances).toEqual(
        mockWorkflowInstances.workflowInstances
      );
    });

    jest.useRealTimers();
  });

  it('should set instances with active operations every time instances are fetched', async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [instance],
              totalCount: 100,
            })
          )
      )
    );
    instancesStore.init();

    await waitFor(() =>
      expect(instancesStore.state.workflowInstances).toEqual([instance])
    );
    expect(instancesStore.state.instancesWithActiveOperations).toEqual([]);

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [
                {id: 'instance_id_1', hasActiveOperation: true},
                {id: 'instance_id_2', hasActiveOperation: false},
              ],
              totalCount: 100,
            })
          )
      ),
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [
                {id: 'instance_id_1', hasActiveOperation: false},
                {id: 'instance_id_2', hasActiveOperation: true},
              ],
              totalCount: 100,
            })
          )
      ),

      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [
                {id: 'instance_id_1', hasActiveOperation: false},
                {id: 'instance_id_2', hasActiveOperation: false},
              ],
              totalCount: 100,
            })
          )
      )
    );

    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });

    await waitFor(() => {
      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        'instance_id_1',
      ]);
    });

    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });

    await waitFor(() => {
      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        'instance_id_2',
      ]);
    });

    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });

    await waitFor(() => {
      expect(instancesStore.state.instancesWithActiveOperations).toEqual([]);
    });
  });

  describe('Remove instances from instances with active operations list', () => {
    it('should remove multiple instances', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(
              ctx.json({
                workflowInstances: [
                  {
                    id: 'instance_id_1',
                    state: 'ACTIVE',
                  },
                  {
                    id: 'instance_id_2',
                    state: 'ACTIVE',
                  },
                  {
                    id: 'instance_id_3',
                    state: 'ACTIVE',
                  },
                  {
                    id: 'instance_id_4',
                    state: 'ACTIVE',
                  },
                ],
                totalCount: 100,
              })
            )
        )
      );
      await instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      instancesStore.addInstancesWithActiveOperations({
        ids: ['instance_id_1', 'instance_id_2', 'instance_id_3'],
      });

      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        'instance_id_1',
        'instance_id_2',
        'instance_id_3',
      ]);

      instancesStore.removeInstanceFromInstancesWithActiveOperations({
        ids: ['instance_id_1', 'instance_id_3'],
      });

      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        'instance_id_2',
      ]);
    });

    it('should remove single instance', async () => {
      await instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      instancesStore.addInstancesWithActiveOperations({
        ids: [instance.id],
      });

      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        instanceWithActiveOperation.id,
        instance.id,
      ]);

      instancesStore.removeInstanceFromInstancesWithActiveOperations({
        ids: [instanceWithActiveOperation.id],
      });

      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        instance.id,
      ]);
    });

    it('should remove instances when polling all visible ids', async () => {
      await instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      instancesStore.addInstancesWithActiveOperations({
        ids: [],
        shouldPollAllVisibleIds: true,
      });

      expect(instancesStore.state.instancesWithActiveOperations).toEqual(
        mockInstances.map(({id}) => id)
      );

      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        )
      );

      instancesStore.removeInstanceFromInstancesWithActiveOperations({
        ids: [],
        shouldPollAllVisibleIds: true,
      });

      await waitFor(() =>
        expect(instancesStore.state.instancesWithActiveOperations).toEqual([
          instanceWithActiveOperation.id,
        ])
      );
    });

    it('should keep polling existing instances with active operations after removing instances when polling all visible ids', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(
              ctx.json({
                workflowInstances: [
                  {
                    id: 'instance_id_1',
                    state: 'ACTIVE',
                    hasActiveOperation: true,
                  },
                  {
                    id: 'instance_id_2',
                    state: 'ACTIVE',
                  },
                  {
                    id: 'instance_id_3',
                    state: 'ACTIVE',
                  },
                  {
                    id: 'instance_id_4',
                    state: 'ACTIVE',
                  },
                ],
                totalCount: 100,
              })
            )
        )
      );

      await instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      instancesStore.addInstancesWithActiveOperations({
        ids: ['instance_id_3'],
        shouldPollAllVisibleIds: true,
      });

      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        'instance_id_1',
        'instance_id_2',
        'instance_id_4',
      ]);

      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(
              ctx.json({
                workflowInstances: [
                  {
                    id: 'instance_id_1',
                    state: 'ACTIVE',
                    hasActiveOperation: true,
                  },
                  {
                    id: 'instance_id_2',
                    state: 'ACTIVE',
                  },
                  {
                    id: 'instance_id_3',
                    state: 'ACTIVE',
                  },
                  {
                    id: 'instance_id_4',
                    state: 'ACTIVE',
                  },
                ],
                totalCount: 100,
              })
            )
        )
      );

      instancesStore.removeInstanceFromInstancesWithActiveOperations({
        ids: [],
        shouldPollAllVisibleIds: true,
      });

      await waitFor(() =>
        expect(instancesStore.state.instancesWithActiveOperations).toEqual([
          'instance_id_1',
        ])
      );
    });

    it('should remove a single instance from instances with active operations list', async () => {
      await instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      instancesStore.addInstancesWithActiveOperations({
        ids: [instance.id],
      });

      expect(instancesStore.state.instancesWithActiveOperations).toHaveLength(
        mockInstances.length
      );
      expect(instancesStore.state.instancesWithActiveOperations).toEqual(
        expect.arrayContaining(mockInstances.map(({id}) => id))
      );

      instancesStore.removeInstanceFromInstancesWithActiveOperations({
        ids: [instance.id],
      });

      expect(instancesStore.state.instancesWithActiveOperations).toEqual([
        instanceWithActiveOperation.id,
      ]);
    });
  });

  describe('fetch instances autorun', () => {
    beforeEach(() => {
      instancesStore.init();
      fetchInstancesSpy.mockReset();
    });

    it('should fetch instances every time sorting changes', async () => {
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(0);
      expect(filtersStore.state.sorting).toEqual(DEFAULT_SORTING);
      filtersStore.setSorting({...DEFAULT_SORTING, sortBy: 'instanceId'});
      expect(filtersStore.state.sorting).toEqual({
        ...DEFAULT_SORTING,
        sortBy: 'instanceId',
      });

      expect(fetchInstancesSpy).toHaveBeenCalledTimes(1);
      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

      filtersStore.setSorting(DEFAULT_SORTING);
      expect(filtersStore.state.sorting).toEqual(DEFAULT_SORTING);

      expect(fetchInstancesSpy).toHaveBeenCalledTimes(2);
    });

    it('should fetch instances every time page or entries per page changes', () => {
      filtersStore.setEntriesPerPage(10);

      expect(filtersStore.state.page).toEqual(1);
      expect(filtersStore.state.prevEntriesPerPage).toEqual(0);
      expect(filtersStore.state.entriesPerPage).toEqual(10);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(0);

      filtersStore.setPage(2);

      expect(filtersStore.state.page).toEqual(2);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(1);

      filtersStore.setEntriesPerPage(5);

      expect(filtersStore.state.page).toEqual(2);
      expect(filtersStore.state.prevEntriesPerPage).toEqual(10);
      expect(filtersStore.state.entriesPerPage).toEqual(5);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(2);

      filtersStore.setEntriesPerPage(10);

      expect(filtersStore.state.page).toEqual(2);
      expect(filtersStore.state.prevEntriesPerPage).toEqual(5);
      expect(filtersStore.state.entriesPerPage).toEqual(10);
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(3);
    });

    it('should fetch instances every time filter changes', () => {
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(0);

      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        startDate: '2020-01-01 12:30:00',
      });
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(1);

      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        startDate: '2022-01-01 12:30:00',
      });
      expect(fetchInstancesSpy).toHaveBeenCalledTimes(2);
    });
  });
});
