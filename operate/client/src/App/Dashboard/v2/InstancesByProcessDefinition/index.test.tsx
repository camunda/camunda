/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, within, screen, waitFor} from 'modules/testing-library';
import {InstancesByProcessDefinition} from './index';
import {
  mockWithSingleVersion,
  mockWithMultipleVersions,
  mockOrderProcessVersions,
} from './index.setup';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessDefinitionVersionStatistics} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionVersionStatistics';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider, type UseQueryResult} from '@tanstack/react-query';
import type {GetProcessDefinitionInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {mockFetchProcessCoreStatistics} from 'modules/mocks/api/processInstances/fetchProcessCoreStatistics';
import {mockFetchIncidentsByError} from 'modules/mocks/api/incidents/fetchIncidentsByError';
import {statistics} from 'modules/mocks/statistics';
import {mockIncidentsByError} from '../IncidentsByError/index.setup';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        panelStatesStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path={Paths.processes()} element={<div>Processes</div>} />
            <Route path={Paths.dashboard()} element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

const createMockQueryResult = (
  overrides: Partial<
    UseQueryResult<GetProcessDefinitionInstanceStatisticsResponseBody>
  > = {},
): UseQueryResult<GetProcessDefinitionInstanceStatisticsResponseBody> => {
  return {
    data: undefined,
    error: null,
    isError: false,
    isPending: false,
    isSuccess: true,
    status: 'success',
    dataUpdatedAt: 0,
    errorUpdateCount: 0,
    errorUpdatedAt: 0,
    failureCount: 0,
    failureReason: null,
    fetchStatus: 'idle',
    isLoading: false,
    isLoadingError: false,
    isFetched: true,
    isFetchedAfterMount: true,
    isFetching: false,
    isInitialLoading: false,
    isPaused: false,
    isPlaceholderData: false,
    isRefetchError: false,
    isRefetching: false,
    isStale: false,
    refetch: vi.fn(),
    ...overrides,
  } as UseQueryResult<GetProcessDefinitionInstanceStatisticsResponseBody>;
};

describe('InstancesByProcessDefinition', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
    mockMe().withSuccess(createUser());
    mockFetchProcessCoreStatistics().withSuccess(statistics);
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);
  });

  it('should display skeleton when loading', () => {
    const result = createMockQueryResult({
      isPending: true,
      isSuccess: false,
      status: 'pending',
      data: undefined,
    });

    render(<InstancesByProcessDefinition result={result} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should handle errors', () => {
    const result = createMockQueryResult({
      isError: true,
      isSuccess: false,
      status: 'error',
      error: new Error('Server error'),
    });

    render(<InstancesByProcessDefinition result={result} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
  });

  it('should render items with more than one processes versions', async () => {
    const result = createMockQueryResult({
      data: mockWithMultipleVersions,
    });

    mockFetchProcessDefinitionVersionStatistics('orderProcess').withSuccess(
      mockOrderProcessVersions,
    );

    const {user} = render(<InstancesByProcessDefinition result={result} />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('instances-by-process-definition-0'),
    );

    const processLink = withinIncident.getByRole('link', {
      description:
        /View 201 Instances in 2\+ Versions of Process Order process/,
    });

    expect(processLink).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=orderProcess&version=all&active=true&incidents=true`,
    );

    expect(
      within(processLink).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('65');
    expect(
      within(processLink).getByTestId('active-instances-badge'),
    ).toHaveTextContent('136');

    const expandButton = withinIncident.getByRole('button', {
      name: 'Expand current row',
    });

    // this button click has no effect (check useEffect in Collapse component)
    await user.click(expandButton);

    const firstVersion = await screen.findByRole('link', {
      description: /View 42 Instances in Version 1 of Process First Version/,
    });

    expect(
      within(firstVersion).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByTestId('active-instances-badge'),
    ).toHaveTextContent('5');
    expect(
      within(firstVersion).getByText(
        'First Version – 42 Instances in Version 1',
      ),
    ).toBeInTheDocument();
    expect(firstVersion).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=mockProcess&version=1&active=true&incidents=true`,
    );

    const secondVersion = screen.getByRole('link', {
      description: 'View 42 Instances in Version 2 of Process Second Version',
    });

    expect(
      within(secondVersion).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('37');
    expect(
      within(secondVersion).getByTestId('active-instances-badge'),
    ).toHaveTextContent('5');
    expect(
      within(secondVersion).getByText(
        'Second Version – 42 Instances in Version 2',
      ),
    ).toBeInTheDocument();
    expect(secondVersion).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=mockProcess&version=2&active=true&incidents=true`,
    );
  });

  it('should render items with one process version', () => {
    const result = createMockQueryResult({
      data: mockWithSingleVersion,
    });

    render(<InstancesByProcessDefinition result={result} />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      screen.getByTestId('instances-by-process-definition-0'),
    );

    expect(
      withinIncident.queryByTestId('expand-button'),
    ).not.toBeInTheDocument();

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version'),
    ).toBeInTheDocument();

    const processLink = screen.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    expect(processLink).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=loanProcess&version=1&active=true&incidents=true`,
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '16',
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '122',
    );
  });

  it('should expand filters panel on click', async () => {
    const result = createMockQueryResult({
      data: mockWithSingleVersion,
    });

    const {user} = render(<InstancesByProcessDefinition result={result} />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    const processLink = screen.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    await user.click(processLink);

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?process=loanProcess&version=1&active=true&incidents=true$/,
      ),
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });
});
