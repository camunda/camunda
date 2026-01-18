/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {PAGE_TITLE} from 'modules/constants';
import {statisticsStore} from 'modules/stores/statistics';
import {Dashboard} from './index';
import {mockIncidentsByError} from './IncidentsByError/index.setup';
import {mockWithSingleVersion} from './InstancesByProcessDefinition/index.setup';
import {statistics} from 'modules/mocks/statistics';
import {mockFetchProcessCoreStatistics} from 'modules/mocks/api/processInstances/fetchProcessCoreStatistics';
import {mockFetchIncidentsByError} from 'modules/mocks/api/incidents/fetchIncidentsByError';
import {mockFetchProcessDefinitionStatistics} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser, searchResult} from 'modules/testUtils';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('Dashboard', () => {
  beforeEach(() => {
    statisticsStore.reset();
    mockMe().withSuccess(createUser());
  });

  it('should render', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
    mockFetchProcessDefinitionStatistics().withSuccess(mockWithSingleVersion);

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('1087 Running Process Instances in total'),
    ).toBeInTheDocument();

    expect(document.title).toBe(PAGE_TITLE.DASHBOARD);
    expect(screen.getByText('Operate Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Process Instances by Name')).toBeInTheDocument();
    expect(
      screen.getByText('Process Incidents by Error Message'),
    ).toBeInTheDocument();
  });

  it('should render empty state (no instances)', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
    mockFetchProcessDefinitionStatistics().withSuccess(searchResult([]));

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Start by deploying a process'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Process Incidents by Error Message'),
    ).not.toBeInTheDocument();
  });

  it('should render empty state (no incidents)', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess([]);
    mockFetchProcessDefinitionStatistics().withSuccess(mockWithSingleVersion);

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Your processes are healthy'),
    ).toBeInTheDocument();
    expect(screen.getByText('Process Instances by Name')).toBeInTheDocument();
    expect(
      screen.getByText('Process Incidents by Error Message'),
    ).toBeInTheDocument();
  });

  it('should display skeleton while loading process definition statistics', () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
    mockFetchProcessDefinitionStatistics().withSuccess(mockWithSingleVersion);

    render(<Dashboard />, {wrapper: Wrapper});

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should handle process definition statistics fetch error', async () => {
    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
    mockFetchProcessDefinitionStatistics().withServerError();

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    expect(
      screen.getByText('Process Incidents by Error Message'),
    ).toBeInTheDocument();
  });

  it('should handle network error for process definition statistics', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
    mockFetchProcessDefinitionStatistics().withNetworkError();

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should update after polling for new process definition statistics', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
    mockFetchProcessDefinitionStatistics().withSuccess(mockWithSingleVersion);

    render(<Dashboard />, {wrapper: Wrapper});

    expect(
      await screen.findByText('loanProcess – 138 Instances in 1 Version'),
    ).toBeInTheDocument();

    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
    mockFetchProcessDefinitionStatistics().withSuccess({
      items: [
        {
          ...mockWithSingleVersion.items[0]!,
          activeInstancesWithoutIncidentCount: 150,
        },
      ],
      page: {totalItems: 1},
    });

    vi.runOnlyPendingTimers();

    expect(
      await screen.findByText('loanProcess – 166 Instances in 1 Version'),
    ).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
