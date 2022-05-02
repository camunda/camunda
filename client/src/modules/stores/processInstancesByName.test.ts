/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstancesByNameStore} from './processInstancesByName';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/dom';

describe('stores/processInstancesByName', () => {
  const mockInstancesByProcess = [
    {
      bpmnProcessId: 'withoutIncidentsProcess',
      processName: 'Without Incidents Process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 28,
      processes: [
        {
          processId: '2251799813685668',
          version: 1,
          name: 'Without Incidents Process',
          bpmnProcessId: 'withoutIncidentsProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 14,
        },
        {
          processId: '2251799813685737',
          version: 2,
          name: 'Without Incidents Process',
          bpmnProcessId: 'withoutIncidentsProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 14,
        },
      ],
    },
    {
      bpmnProcessId: 'bigVarProcess',
      processName: 'Big variable process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 1,
      processes: [
        {
          processId: '2251799813686019',
          version: 1,
          name: 'Big variable process',
          bpmnProcessId: 'bigVarProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 1,
        },
      ],
    },
  ];

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockInstancesByProcess))
      )
    );
  });

  afterEach(() => {
    processInstancesByNameStore.reset();
  });

  it('should get process instances by name', async () => {
    expect(processInstancesByNameStore.state.status).toBe('initial');
    processInstancesByNameStore.getProcessInstancesByName();

    expect(processInstancesByNameStore.state.status).toBe('first-fetch');
    await waitFor(() => {
      expect(processInstancesByNameStore.state.processInstances).toEqual(
        mockInstancesByProcess
      );
    });
  });

  it('should start polling on init', async () => {
    jest.useFakeTimers();
    processInstancesByNameStore.init();
    await waitFor(() =>
      expect(processInstancesByNameStore.state.status).toBe('fetched')
    );

    expect(processInstancesByNameStore.state.processInstances).toEqual(
      mockInstancesByProcess
    );

    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(processInstancesByNameStore.state.processInstances).toEqual([]);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set failed response on error', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occurred'}))
      )
    );
    await processInstancesByNameStore.getProcessInstancesByName();
    expect(processInstancesByNameStore.state.status).toBe('error');
    expect(processInstancesByNameStore.state.processInstances).toEqual([]);
  });

  it('should reset store', async () => {
    await processInstancesByNameStore.getProcessInstancesByName();
    expect(processInstancesByNameStore.state.status).toBe('fetched');
    expect(processInstancesByNameStore.state.processInstances).toEqual(
      mockInstancesByProcess
    );

    processInstancesByNameStore.reset();
    expect(processInstancesByNameStore.state.status).toBe('initial');
    expect(processInstancesByNameStore.state.processInstances).toEqual([]);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstancesByNameStore.getProcessInstancesByName();

    await waitFor(() =>
      expect(processInstancesByNameStore.state.processInstances).toEqual(
        mockInstancesByProcess
      )
    );

    const newMockInstancesByProcess = [
      ...mockInstancesByProcess,
      {
        bpmnProcessId: 'anotherProcess',
        processName: 'Another Process',
        instancesWithActiveIncidentsCount: 5,
        activeInstancesCount: 30,
        processes: [],
      },
    ];
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(newMockInstancesByProcess))
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(processInstancesByNameStore.state.processInstances).toEqual(
        newMockInstancesByProcess
      )
    );

    window.addEventListener = originalEventListener;
  });
});
