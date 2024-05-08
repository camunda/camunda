/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesByNameStore} from './processInstancesByName';
import {waitFor} from 'modules/testing-library';
import {mockFetchProcessInstancesByName} from 'modules/mocks/api/incidents/fetchProcessInstancesByName';

describe('stores/processInstancesByName', () => {
  const mockInstancesByProcess = [
    {
      bpmnProcessId: 'withoutIncidentsProcess',
      tenantId: '<default>',
      processName: 'Without Incidents Process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 28,
      processes: [
        {
          processId: '2251799813685668',
          tenantId: '<default>',
          version: 1,
          name: 'Without Incidents Process',
          bpmnProcessId: 'withoutIncidentsProcess',
          errorMessage: null,
          instancesWithActiveIncidentsCount: 0,
          activeInstancesCount: 14,
        },
        {
          processId: '2251799813685737',
          tenantId: '<default>',
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
      tenantId: '<default>',
      processName: 'Big variable process',
      instancesWithActiveIncidentsCount: 0,
      activeInstancesCount: 1,
      processes: [
        {
          processId: '2251799813686019',
          tenantId: '<default>',
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
    mockFetchProcessInstancesByName().withSuccess(mockInstancesByProcess, {
      expectPolling: false,
    });
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
        mockInstancesByProcess,
      );
    });
  });

  it('should start polling on init', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockInstancesByProcess, {
      expectPolling: true,
    });
    jest.useFakeTimers();
    processInstancesByNameStore.init();
    await waitFor(() =>
      expect(processInstancesByNameStore.state.status).toBe('fetched'),
    );

    expect(processInstancesByNameStore.state.processInstances).toEqual(
      mockInstancesByProcess,
    );

    mockFetchProcessInstancesByName().withSuccess([], {expectPolling: true});

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(processInstancesByNameStore.state.processInstances).toEqual([]);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set failed response on error', async () => {
    mockFetchProcessInstancesByName().withServerError();

    await processInstancesByNameStore.getProcessInstancesByName();
    expect(processInstancesByNameStore.state.status).toBe('error');
    expect(processInstancesByNameStore.state.processInstances).toEqual([]);
  });

  it('should reset store', async () => {
    await processInstancesByNameStore.getProcessInstancesByName();
    expect(processInstancesByNameStore.state.status).toBe('fetched');
    expect(processInstancesByNameStore.state.processInstances).toEqual(
      mockInstancesByProcess,
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
        mockInstancesByProcess,
      ),
    );

    const newMockInstancesByProcess = [
      ...mockInstancesByProcess,
      {
        bpmnProcessId: 'anotherProcess',
        tenantId: '<default>',
        processName: 'Another Process',
        instancesWithActiveIncidentsCount: 5,
        activeInstancesCount: 30,
        processes: [],
      },
    ];
    mockFetchProcessInstancesByName().withSuccess(newMockInstancesByProcess);

    eventListeners.online();

    await waitFor(() =>
      expect(processInstancesByNameStore.state.processInstances).toEqual(
        newMockInstancesByProcess,
      ),
    );

    window.addEventListener = originalEventListener;
  });
});
