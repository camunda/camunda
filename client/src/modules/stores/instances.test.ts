/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instancesStore} from './instances';
import {groupedProcessesMock} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {createOperation} from 'modules/utils/instance';

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
  parentInstanceId: null,
  rootInstanceId: null,
  callHierarchy: [],
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
  parentInstanceId: null,
  rootInstanceId: null,
  callHierarchy: [],
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
    instancesStore.reset();
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

    instancesStore.refreshAllInstances();
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

    instancesStore.refreshAllInstances();
    await waitFor(() =>
      expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
        '2251799813685625',
        '2251799813685627',
      ])
    );
  });

  it('should reset store', async () => {
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

    instancesStore.reset();
    expect(instancesStore.state).toEqual({
      filteredInstancesCount: 0,
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
    const cancelOperation = createOperation('CANCEL_PROCESS_INSTANCE');
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

    instancesStore.markInstancesWithActiveOperations({
      ids: ['1', '2'],
      operationType: 'CANCEL_PROCESS_INSTANCE',
    });

    expect(instancesStore.state.processInstances).toEqual([
      {
        ...instance,
        id: '1',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
      {
        ...instance,
        id: '2',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
      {...instance, id: '3'},
    ]);
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '2']);

    instancesStore.markInstancesWithActiveOperations({
      ids: ['non_existing_instance_id'],
      operationType: 'CANCEL_PROCESS_INSTANCE',
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '2']);
    expect(instancesStore.state.processInstances).toEqual([
      {
        ...instance,
        id: '1',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
      {
        ...instance,
        id: '2',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
      {...instance, id: '3'},
    ]);

    instancesStore.markInstancesWithActiveOperations({
      ids: [],
      shouldPollAllVisibleIds: true,
      operationType: 'CANCEL_PROCESS_INSTANCE',
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '1',
      '2',
      '3',
    ]);

    expect(instancesStore.state.processInstances).toEqual([
      {
        ...instance,
        id: '1',
        hasActiveOperation: true,
        operations: [cancelOperation, cancelOperation],
      },
      {
        ...instance,
        id: '2',
        hasActiveOperation: true,
        operations: [cancelOperation, cancelOperation],
      },
      {
        ...instance,
        id: '3',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
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
      operationType: 'CANCEL_PROCESS_INSTANCE',
    });
    expect(instancesStore.state.processInstances).toEqual([
      {
        ...instance,
        id: '1',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
      {
        ...instance,
        id: '2',
      },
      {
        ...instance,
        id: '3',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
    ]);
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '3']);

    instancesStore.markInstancesWithActiveOperations({
      ids: ['non_existing_instance_id'],
      shouldPollAllVisibleIds: true,
      operationType: 'CANCEL_PROCESS_INSTANCE',
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual([
      '1',
      '2',
      '3',
    ]);

    expect(instancesStore.state.processInstances).toEqual([
      {
        ...instance,
        id: '1',
        hasActiveOperation: true,
        operations: [cancelOperation, cancelOperation],
      },
      {
        ...instance,
        id: '2',
        hasActiveOperation: true,
        operations: [cancelOperation],
      },
      {
        ...instance,
        id: '3',
        hasActiveOperation: true,
        operations: [cancelOperation, cancelOperation],
      },
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
      operationType: 'CANCEL_PROCESS_INSTANCE',
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
      operationType: 'CANCEL_PROCESS_INSTANCE',
    });
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1', '2']);
  });

  it('should not set active operation state to false if there are still running operations', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            totalCount: 100,
            processInstances: [
              {
                ...instanceWithActiveOperation,
                id: '1',
                operations: [
                  {
                    errorMessage: 'string',
                    state: 'SENT',
                    type: 'RESOLVE_INCIDENT',
                  },
                  {
                    errorMessage: 'string',
                    state: 'SENT',
                    type: 'CANCEL_PROCESS_INSTANCE',
                  },
                ],
              },
            ],
          })
        )
      )
    );

    instancesStore.fetchInstancesFromFilters();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1']);

    instancesStore.unmarkInstancesWithActiveOperations({
      instanceIds: ['1'],
      shouldPollAllVisibleIds: false,
      operationType: 'RESOLVE_INCIDENT',
    });

    expect(instancesStore.instanceIdsWithActiveOperations).toEqual(['1']);
    expect(instancesStore.state.processInstances[0].hasActiveOperation).toBe(
      true
    );
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
