/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from '@testing-library/react';
import {mockProcessStatistics} from 'modules/mocks/mockProcessStatistics';
import {ProcessStatistics} from './processStatistics.base';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';

const processStatisticsStore = new ProcessStatistics();

describe('stores/processStatistics.base', () => {
  afterEach(() => {
    processStatisticsStore.reset();
  });

  it('should fetch process statistics', async () => {
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);

    expect(processStatisticsStore.state.statistics).toEqual([]);

    processStatisticsStore.fetchProcessStatistics();
    await waitFor(() =>
      expect(processStatisticsStore.statistics).toEqual(mockProcessStatistics),
    );
  });

  it('should get flowNodeStates', async () => {
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);

    processStatisticsStore.fetchProcessStatistics();

    // wait for statistics to be fetched
    await waitFor(() =>
      expect(processStatisticsStore.statistics).not.toEqual([]),
    );

    expect(processStatisticsStore.flowNodeStates).toEqual([
      {
        count: 1,
        flowNodeId: 'userTask',
        flowNodeState: 'active',
      },
      {
        count: 2,
        flowNodeId: 'userTask',
        flowNodeState: 'canceled',
      },
      {
        count: 3,
        flowNodeId: 'EndEvent_0crvjrk',
        flowNodeState: 'incidents',
      },
      {
        count: 4,
        flowNodeId: 'EndEvent_0crvjrk',
        flowNodeState: 'canceled',
      },
    ]);
  });

  it('should get overlaysData', async () => {
    mockFetchProcessInstancesStatistics().withSuccess([
      ...mockProcessStatistics,
      {
        activityId: 'EndEvent_2',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 5,
      },
    ]);

    processStatisticsStore.fetchProcessStatistics();

    // wait for statistics to be fetched
    await waitFor(() =>
      expect(processStatisticsStore.overlaysData).not.toEqual([]),
    );

    expect(processStatisticsStore.overlaysData).toEqual([
      {
        flowNodeId: 'userTask',
        payload: {
          count: 1,
          flowNodeState: 'active',
        },
        position: {
          bottom: 9,
          left: 0,
        },
        type: 'statistics-active',
      },
      {
        flowNodeId: 'userTask',
        payload: {
          count: 2,
          flowNodeState: 'canceled',
        },
        position: {
          left: 0,
          top: -16,
        },
        type: 'statistics-canceled',
      },
      {
        flowNodeId: 'EndEvent_0crvjrk',
        payload: {
          count: 3,
          flowNodeState: 'incidents',
        },
        position: {
          bottom: 9,
          right: 0,
        },
        type: 'statistics-incidents',
      },
      {
        flowNodeId: 'EndEvent_0crvjrk',
        payload: {
          count: 4,
          flowNodeState: 'canceled',
        },
        position: {
          left: 0,
          top: -16,
        },
        type: 'statistics-canceled',
      },
      {
        flowNodeId: 'EndEvent_2',
        payload: {
          count: 5,
          flowNodeState: 'completedEndEvents',
        },
        position: {
          bottom: 1,
          left: 17,
        },
        type: 'statistics-completedEndEvents',
      },
    ]);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchProcessInstancesStatistics().withNetworkError();

    // processStatisticsStore.init();
    processStatisticsStore.fetchProcessStatistics();

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual([]),
    );

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);

    eventListeners.online();

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        mockProcessStatistics,
      ),
    );

    consoleErrorMock.mockRestore();
    window.addEventListener = originalEventListener;
  });
});
