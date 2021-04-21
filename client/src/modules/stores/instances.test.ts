/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instancesStore} from './instances';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';
import {groupedProcessesMock} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';

const instance: ProcessInstanceEntity = {
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
  sortValues: ['', ''],
};

const instanceWithActiveOperation: ProcessInstanceEntity = {
  id: '2251799813685627',
  processId: '2251799813685623',
  processName: 'Without Incidents Process',
  processVersion: 1,
  startDate: '2020-11-19T08:14:05.490+0000',
  endDate: null,
  state: 'ACTIVE',
  bpmnProcessId: 'withoutIncidentsProcess',
  hasActiveOperation: true,
  operations: [],
  sortValues: ['', ''],
};

const mockInstances = [instance, instanceWithActiveOperation];

const mockProcessInstances = {
  processInstances: mockInstances,
  totalCount: 100,
};

describe('stores/instances', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedProcessesMock))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );
  });
  afterEach(() => {
    clearStateLocally();
    instancesStore.reset();
  });

  describe('filtered instances count', () => {
    it('should return null by default', () => {
      expect(instancesStore.state.filteredInstancesCount).toBe(null);
    });

    // This test is skipped, because setting the local storage inside
    // the test has no effect. See https://jira.camunda.com/browse/OPE-1004
    it.skip('should return from local storage', () => {
      instancesStore.reset();
      storeStateLocally({filteredInstancesCount: 312});

      expect(instancesStore.state.filteredInstancesCount).toBe(312);
    });

    it('should return store state', () => {
      instancesStore.setInstances({
        filteredInstancesCount: 654,
        processInstances: [],
      });

      expect(instancesStore.state.filteredInstancesCount).toBe(654);
    });

    it('should return store state when both is set', () => {
      storeStateLocally({filteredInstancesCount: 101});
      instancesStore.setInstances({
        filteredInstancesCount: 202,
        processInstances: [],
      });

      expect(instancesStore.state.filteredInstancesCount).toBe(202);
    });
  });

  it('should fetch initial instances', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    expect(instancesStore.state.status).toBe('initial');

    instancesStore.fetchInstancesFromFilters();

    expect(instancesStore.state.status).toBe('first-fetch');

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    expect(instancesStore.state.filteredInstancesCount).toBe(100);
    expect(instancesStore.state.processInstances).toEqual(
      mockProcessInstances.processInstances
    );
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '2251799813685627',
    ]);
  });

  it('should fetch next instances', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    expect(instancesStore.state.status).toBe('initial');

    instancesStore.fetchInstancesFromFilters();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            processInstances: [
              {...instance, id: '100'},
              {...instance, hasActiveOperation: true, id: '101'},
            ],
          })
        )
      )
    );

    instancesStore.fetchNextInstances();

    expect(instancesStore.state.status).toBe('fetching-next');
    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    expect(instancesStore.state.processInstances.length).toBe(4);
    expect(instancesStore.state.processInstances[2].id).toBe('100');
    expect(instancesStore.state.processInstances[3].id).toBe('101');
    expect(instancesStore.state.latestFetch).toEqual({
      fetchType: 'next',
      processInstancesCount: 2,
    });
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            processInstances: [{...instance, id: '200'}],
          })
        )
      )
    );

    instancesStore.fetchNextInstances();

    expect(instancesStore.state.status).toBe('fetching-next');
    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    expect(instancesStore.state.processInstances.length).toBe(5);
    expect(instancesStore.state.processInstances[4].id).toBe('200');
    expect(instancesStore.state.latestFetch).toEqual({
      fetchType: 'next',
      processInstancesCount: 1,
    });

    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '2251799813685627',
      '101',
    ]);
  });

  it('should fetch previous instances', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    expect(instancesStore.state.status).toBe('initial');

    instancesStore.fetchInstancesFromFilters();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            processInstances: [{...instance, id: '100'}],
          })
        )
      )
    );

    instancesStore.fetchPreviousInstances();

    expect(instancesStore.state.status).toBe('fetching-prev');
    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    expect(instancesStore.state.processInstances.length).toBe(3);
    expect(instancesStore.state.processInstances[0].id).toBe('100');
    expect(instancesStore.state.latestFetch).toEqual({
      fetchType: 'prev',
      processInstancesCount: 1,
    });
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            ...mockProcessInstances,
            processInstances: [{...instance, id: '200'}],
          })
        )
      )
    );

    instancesStore.fetchPreviousInstances();

    expect(instancesStore.state.status).toBe('fetching-prev');
    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    expect(instancesStore.state.processInstances.length).toBe(4);
    expect(instancesStore.state.processInstances[0].id).toBe('200');
    expect(instancesStore.state.processInstances[1].id).toBe('100');
    expect(instancesStore.state.latestFetch).toEqual({
      fetchType: 'prev',
      processInstancesCount: 1,
    });

    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '2251799813685627',
    ]);
  });

  it('should refresh all instances', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    instancesStore.fetchInstancesFromFilters();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '2251799813685627',
    ]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              instance,
              {...instanceWithActiveOperation, hasActiveOperation: false},
            ],
            totalCount: 100,
          })
        )
      )
    );

    instancesStore.refreshAllInstances({query: {}});
    await waitFor(() =>
      expect(instancesStore.instanceIdsWithActiveOperations).toEqual([])
    );

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              {...instance, hasActiveOperation: true},
              {...instanceWithActiveOperation},
            ],
            totalCount: 100,
          })
        )
      )
    );

    instancesStore.refreshAllInstances({query: {}});
    await waitFor(() =>
      expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
        '2251799813685625',
        '2251799813685627',
      ])
    );
  });

  it('should reset store (keep the filteredInstancesCount value)', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await instancesStore.fetchInstances({
      fetchType: 'initial',
      payload: {query: {}},
    });

    expect(instancesStore.state.processInstances).toEqual(
      mockProcessInstances.processInstances
    );
    const filteredInstancesCount = instancesStore.state.filteredInstancesCount;
    instancesStore.reset();
    expect(instancesStore.state).toEqual({
      filteredInstancesCount: filteredInstancesCount,
      processInstances: [],
      status: 'initial',
      latestFetch: null,
    });
  });

  it('should get visible ids in list panel', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await instancesStore.fetchInstances({
      fetchType: 'initial',
      payload: {query: {}},
    });

    expect(instancesStore.visibleIdsInListPanel).toEqual(
      mockInstances.map(({id}) => id)
    );
  });

  it('should get areProcessInstancesEmpty', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    await instancesStore.fetchInstances({
      fetchType: 'initial',
      payload: {query: {}},
    });

    expect(instancesStore.areProcessInstancesEmpty).toBe(false);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({processInstances: [], totalCount: 0}))
      )
    );

    await instancesStore.fetchInstances({
      fetchType: 'initial',
      payload: {query: {}},
    });

    expect(instancesStore.areProcessInstancesEmpty).toBe(true);
  });

  it('should mark instances with active operations', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: [
              {...instance, id: '1'},
              {...instance, id: '2'},
              {...instance, id: '3'},
            ],
          })
        )
      )
    );

    await instancesStore.fetchInstances({
      fetchType: 'initial',
      payload: {query: {}},
    });

    instancesStore.markInstancesWithActiveOperations({ids: ['1', '2']});

    expect(instancesStore.state.processInstances).toEqual([
      {...instance, id: '1', hasActiveOperation: true},
      {...instance, id: '2', hasActiveOperation: true},
      {...instance, id: '3'},
    ]);
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '2']);

    instancesStore.markInstancesWithActiveOperations({
      ids: ['non_existing_instance_id'],
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '2']);
    expect(instancesStore.state.processInstances).toEqual([
      {...instance, id: '1', hasActiveOperation: true},
      {...instance, id: '2', hasActiveOperation: true},
      {...instance, id: '3'},
    ]);

    instancesStore.markInstancesWithActiveOperations({
      ids: [],
      shouldPollAllVisibleIds: true,
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '1',
      '2',
      '3',
    ]);

    expect(instancesStore.state.processInstances).toEqual([
      {...instance, id: '1', hasActiveOperation: true},
      {...instance, id: '2', hasActiveOperation: true},
      {...instance, id: '3', hasActiveOperation: true},
    ]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: [
              {...instance, id: '1'},
              {...instance, id: '2'},
              {...instance, id: '3'},
            ],
          })
        )
      )
    );

    await instancesStore.fetchInstances({
      fetchType: 'initial',
      payload: {query: {}},
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([]);

    expect(instancesStore.state.processInstances).toEqual([
      {...instance, id: '1'},
      {...instance, id: '2'},
      {...instance, id: '3'},
    ]);

    instancesStore.markInstancesWithActiveOperations({
      ids: ['2'],
      shouldPollAllVisibleIds: true,
    });
    expect(instancesStore.state.processInstances).toEqual([
      {...instance, id: '1', hasActiveOperation: true},
      {...instance, id: '2'},
      {...instance, id: '3', hasActiveOperation: true},
    ]);
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '3']);

    instancesStore.markInstancesWithActiveOperations({
      ids: ['non_existing_instance_id'],
      shouldPollAllVisibleIds: true,
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '1',
      '2',
      '3',
    ]);

    expect(instancesStore.state.processInstances).toEqual([
      {...instance, id: '1', hasActiveOperation: true},
      {...instance, id: '2', hasActiveOperation: true},
      {...instance, id: '3', hasActiveOperation: true},
    ]);
  });

  it('should unmark instances with active operations', async () => {
    // when polling all visible instances
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: [
              {...instanceWithActiveOperation, id: '1'},
              {...instanceWithActiveOperation, id: '2'},
              {...instanceWithActiveOperation, id: '3'},
            ],
          })
        )
      )
    );

    instancesStore.fetchInstancesFromFilters();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '1',
      '2',
      '3',
    ]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: [
              {...instance, id: '1'},
              {...instance, id: '2'},
              {...instance, id: '3'},
            ],
          })
        )
      )
    );

    instancesStore.unmarkInstancesWithActiveOperations({
      instanceIds: ['1', '2', '3'],
      shouldPollAllVisibleIds: true,
    });

    await waitFor(() =>
      expect(instancesStore.instanceIdsWithActiveOperations).toEqual([])
    );

    // when not polling all visible instances
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: [
              {...instanceWithActiveOperation, id: '1'},
              {...instanceWithActiveOperation, id: '2'},
              {...instanceWithActiveOperation, id: '3'},
            ],
          })
        )
      )
    );

    instancesStore.fetchInstancesFromFilters();
    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '1',
      '2',
      '3',
    ]);

    instancesStore.unmarkInstancesWithActiveOperations({
      instanceIds: ['3'],
      shouldPollAllVisibleIds: false,
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '2']);
  });

  it('should refresh instances and and call handlers every time there is an instance with completed operation', async () => {
    jest.useFakeTimers();
    const handlerMock = jest.fn();
    instancesStore.addCompletedOperationsHandler(handlerMock);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();

    await waitFor(() =>
      expect(instancesStore.state.processInstances).toHaveLength(2)
    );
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '2251799813685627',
    ]);

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              instance,
              {...instanceWithActiveOperation, hasActiveOperation: false},
            ],
            totalCount: 2,
          })
        )
      ),
      //refresh
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              instance,
              {...instanceWithActiveOperation, hasActiveOperation: false},
            ],
            totalCount: 3,
          })
        )
      )
    );

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(instancesStore.state.filteredInstancesCount).toBe(3)
    );

    expect(handlerMock).toHaveBeenCalledTimes(1);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should poll instances by id when there are instances with active operations', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: [
              {...instance, id: '1'},
              {...instanceWithActiveOperation, id: '2'},
            ],
          })
        )
      )
    );

    jest.useFakeTimers();

    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();

    await waitFor(() =>
      expect(instancesStore.state.processInstances).toHaveLength(2)
    );
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['2']);
    mockServer.use(
      // mock for fetching instances when there is an active operation
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              {
                ...instanceWithActiveOperation,
                hasActiveOperation: false,
                id: '2',
              },
            ],
            totalCount: 100,
          })
        )
      ),
      // mock for refreshing instances when an instance operation is completed
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [
              {...instance, id: '1'},
              {
                ...instanceWithActiveOperation,
                id: '2',
                hasActiveOperation: false,
              },
            ],
            totalCount: 2,
          })
        )
      )
    );

    jest.runOnlyPendingTimers();
    await waitFor(() => {
      expect(instancesStore.state.filteredInstancesCount).toEqual(2);
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([]);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    const mockedProcessInstances = {
      processInstances: [instance],
      totalCount: 1,
    };

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockedProcessInstances))
      )
    );

    instancesStore.init();
    instancesStore.fetchInstancesFromFilters();

    await waitFor(() =>
      expect(instancesStore.state.processInstances).toEqual(
        mockedProcessInstances.processInstances
      )
    );

    const newProcessInstancesResponse = {
      processInstances: [instance, {...instance, id: '123'}],
      totalCount: 2,
    };

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(newProcessInstancesResponse))
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(instancesStore.state.processInstances).toEqual(
        newProcessInstancesResponse.processInstances
      )
    );

    window.addEventListener = originalEventListener;
  });
});
