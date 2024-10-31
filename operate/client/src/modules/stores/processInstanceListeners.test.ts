/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createInstance} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceListeners} from 'modules/mocks/api/processInstances/fetchProcessInstanceListeners';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';

import {processInstanceListenersStore} from './processInstanceListeners';

const instance: ListenerEntity = {
  listenerType: 'EXECUTION_LISTENER',
  listenerKey: '0000000000000001',
  state: 'ACTIVE',
  event: 'START',
  jobType: 'EVENT',
  time: '2024-01-02 19:51:16',
  sortValues: ['1'],
};

const failedInstance: ListenerEntity = {
  listenerType: 'EXECUTION_LISTENER',
  listenerKey: '0000000000000002',
  state: 'FAILED',
  event: 'END',
  jobType: 'EVENT',
  time: '2024-01-02 21:11:12',
  sortValues: ['2'],
};

const mockInstances = [instance, failedInstance];

const mockListenerInstances = {listeners: mockInstances, totalCount: 100};

describe('stores/processInstancesListeners', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({id: '123', state: 'ACTIVE'}),
    );

    mockFetchProcessInstanceListeners().withSuccess(mockListenerInstances);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
      flowNodeInstanceId: '123',
    });

    await processInstanceDetailsStore.fetchProcessInstance('123');
  });

  afterEach(() => {
    processInstanceListenersStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
  });

  it('should fetch initial listeners', async () => {
    processInstanceListenersStore.fetchListeners({
      fetchType: 'initial',
      processInstanceId: '123',
      payload: {pageSize: mockListenerInstances.totalCount, flowNodeId: '123'},
    });

    expect(processInstanceListenersStore.state.status).toBe('first-fetch');

    await waitFor(() =>
      expect(processInstanceListenersStore.state.listeners).toEqual(
        mockListenerInstances.listeners,
      ),
    );

    expect(processInstanceListenersStore.state.status).toBe('fetched');
  });

  it('should fetch next listeners', async () => {
    expect(processInstanceListenersStore.state.status).toBe('initial');

    processInstanceListenersStore.fetchListeners({
      fetchType: 'initial',
      processInstanceId: '123',
      payload: {pageSize: mockListenerInstances.totalCount, flowNodeId: '123'},
    });

    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    mockFetchProcessInstanceListeners().withSuccess({
      ...mockListenerInstances,
      listeners: [
        {...instance, listenerKey: '100'},
        {...instance, listenerKey: '101'},
      ],
    });

    processInstanceListenersStore.fetchNextListeners();

    expect(processInstanceListenersStore.state.status).toBe('fetching-next');
    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    expect(processInstanceListenersStore.state.listeners.length).toBe(4);
    expect(processInstanceListenersStore.state.listeners[2]?.listenerKey).toBe(
      '100',
    );
    expect(processInstanceListenersStore.state.listeners[3]?.listenerKey).toBe(
      '101',
    );
    expect(processInstanceListenersStore.state.latestFetch).toEqual({
      fetchType: 'next',
      itemsCount: 2,
    });

    mockFetchProcessInstanceListeners().withSuccess({
      ...mockListenerInstances,
      listeners: [{...instance, listenerKey: '200'}],
    });

    processInstanceListenersStore.fetchNextListeners();

    expect(processInstanceListenersStore.state.status).toBe('fetching-next');
    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    expect(processInstanceListenersStore.state.listeners.length).toBe(5);
    expect(processInstanceListenersStore.state.listeners[4]?.listenerKey).toBe(
      '200',
    );
    expect(processInstanceListenersStore.state.latestFetch).toEqual({
      fetchType: 'next',
      itemsCount: 1,
    });
  });

  it('should fetch previous listeners', async () => {
    expect(processInstanceListenersStore.state.status).toBe('initial');

    processInstanceListenersStore.fetchListeners({
      fetchType: 'initial',
      processInstanceId: '123',
      payload: {pageSize: mockListenerInstances.totalCount, flowNodeId: '123'},
    });

    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    mockFetchProcessInstanceListeners().withSuccess({
      ...mockListenerInstances,
      listeners: [{...instance, listenerKey: '100'}],
    });

    processInstanceListenersStore.fetchPreviousListeners();

    expect(processInstanceListenersStore.state.status).toBe('fetching-prev');
    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    expect(processInstanceListenersStore.state.listeners.length).toBe(3);
    expect(processInstanceListenersStore.state.listeners[0]?.listenerKey).toBe(
      '100',
    );
    expect(processInstanceListenersStore.state.latestFetch).toEqual({
      fetchType: 'prev',
      itemsCount: 1,
    });

    mockFetchProcessInstanceListeners().withSuccess({
      ...mockListenerInstances,
      listeners: [{...instance, listenerKey: '200'}],
    });

    processInstanceListenersStore.fetchPreviousListeners();

    expect(processInstanceListenersStore.state.status).toBe('fetching-prev');
    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    expect(processInstanceListenersStore.state.listeners.length).toBe(4);
    expect(processInstanceListenersStore.state.listeners[0]?.listenerKey).toBe(
      '200',
    );
    expect(processInstanceListenersStore.state.listeners[1]?.listenerKey).toBe(
      '100',
    );
    expect(processInstanceListenersStore.state.latestFetch).toEqual({
      fetchType: 'prev',
      itemsCount: 1,
    });
  });

  it('should get correct number of listener incidents', async () => {
    processInstanceListenersStore.fetchListeners({
      fetchType: 'initial',
      processInstanceId: '123',
      payload: {pageSize: mockListenerInstances.totalCount, flowNodeId: '123'},
    });

    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    expect(processInstanceListenersStore.listenersFailureCount).toBe(1);

    mockFetchProcessInstanceListeners().withSuccess({
      ...mockListenerInstances,
      listeners: [...mockInstances, failedInstance, failedInstance],
    });

    processInstanceListenersStore.fetchListeners({
      fetchType: 'initial',
      processInstanceId: '123',
      payload: {pageSize: mockListenerInstances.totalCount, flowNodeId: '123'},
    });

    await waitFor(() =>
      expect(processInstanceListenersStore.state.status).toBe('fetched'),
    );

    expect(processInstanceListenersStore.listenersFailureCount).toBe(3);
  });

  it('should reset store', async () => {
    await processInstanceListenersStore.fetchListeners({
      fetchType: 'initial',
      processInstanceId: '123',
      payload: {pageSize: 50, flowNodeId: '123'},
    });

    expect(processInstanceListenersStore.state.listeners).toEqual(
      mockListenerInstances.listeners,
    );
    expect(processInstanceListenersStore.state.status).toBe('fetched');
    expect(processInstanceListenersStore.state.listenersCount).toBe(100);
    processInstanceListenersStore.reset();
    expect(processInstanceListenersStore.state.listeners).toEqual([]);
    expect(processInstanceListenersStore.state.status).toBe('initial');
    expect(processInstanceListenersStore.state.listenersCount).toBe(0);
  });
});
