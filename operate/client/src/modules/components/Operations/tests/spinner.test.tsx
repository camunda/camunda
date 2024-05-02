/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {groupedProcessesMock} from 'modules/testUtils';
import {processInstancesStore} from 'modules/stores/processInstances';
import {Operations} from '../index';
import {INSTANCE, ACTIVE_INSTANCE, Wrapper} from './mocks';

describe('Operations - Spinner', () => {
  it('should not display spinner', () => {
    render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
        }}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
  });

  it('should display spinner if it is forced', () => {
    render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
        }}
        forceSpinner={true}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should display spinner if incident id is included in instances with active operations', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstances().withSuccess({
      processInstances: [ACTIVE_INSTANCE],
      totalCount: 1,
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    render(
      <Operations
        instance={{
          ...ACTIVE_INSTANCE,
          state: 'INCIDENT',
        }}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );
    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    jest.runOnlyPendingTimers();

    // mock for refresh all instances
    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    // mock for refresh running process instances count
    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
