/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  within,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {IncidentsByError} from './index';
import {
  mockIncidentsByError,
  mockIncidentStatisticsByErrorWithBigMessage,
  mockIncidentStatisticsByDefinition,
  bigErrorMessage,
} from './index.setup';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';
import {mockFetchIncidentProcessInstanceStatisticsByError} from 'modules/mocks/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByError';
import {mockFetchIncidentProcessInstanceStatisticsByDefinition} from 'modules/mocks/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByDefinition';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser, searchResult} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';
import {useEffect} from 'react';
import {panelStatesStore} from 'modules/stores/panelStates';
import {truncateErrorMessage} from './utils/truncateErrorMessage';
import * as clientConfig from 'modules/utils/getClientConfig';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => () => panelStatesStore.reset(), []);

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

describe('IncidentsByError', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser());
  });

  const mockIncidentQueries = ({
    incidents = mockIncidentsByError,
    definitions = mockIncidentStatisticsByDefinition,
  } = {}) => {
    mockFetchIncidentProcessInstanceStatisticsByError().withSuccess(incidents);
    mockFetchIncidentProcessInstanceStatisticsByError().withSuccess(incidents);

    mockFetchIncidentProcessInstanceStatisticsByDefinition().withSuccess(
      definitions,
    );
    mockFetchIncidentProcessInstanceStatisticsByDefinition().withSuccess(
      definitions,
    );
  };

  it('should display skeleton when loading', async () => {
    mockIncidentQueries();

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(await screen.findByTestId('incident-byError')).toBeInTheDocument();
  });

  it('should handle server errors', async () => {
    mockFetchIncidentProcessInstanceStatisticsByError().withServerError();

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should handle network errors', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockFetchIncidentProcessInstanceStatisticsByError().withNetworkError();

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should display information message when there are no incidents', async () => {
    mockIncidentQueries({incidents: searchResult([])});

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Your processes are healthy'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('There are no incidents on any instances.'),
    ).toBeInTheDocument();
  });

  it('should render process incidents with expandable process list', async () => {
    mockIncidentQueries();

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0'),
    );

    const expandButton = withinIncident.getByRole('button', {
      name: 'Expand current row',
    });

    expect(expandButton).toBeInTheDocument();

    const processLink = withinIncident.getByRole('link', {
      description:
        "View 78 Instances with error JSON path '$.paid' has no result.",
    });

    expect(processLink).toHaveAttribute(
      'href',
      `${Paths.processes()}?errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidentErrorHashCode=234254&incidents=true`,
    );

    await user.click(expandButton);

    const firstProcess = await screen.findByRole('link', {
      description:
        "View 52 Instances with error JSON path '$.paid' has no result. in version 1 of Process Call Level 2 Process – Version 1",
    });

    expect(
      within(firstProcess).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('52');
    expect(firstProcess).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=call-level-2-process&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidentErrorHashCode=234254&incidents=true`,
    );
  });

  it('should include tenant in link when multi-tenancy is enabled', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });

    mockIncidentQueries();

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const expandButton = within(
      await screen.findByTestId('incident-byError-0'),
    ).getByRole('button', {name: 'Expand current row'});

    await user.click(expandButton);

    const tenantProcess = await screen.findByRole('link', {
      description:
        "View 26 Instances with error JSON path '$.paid' has no result. in version 1 of Process Process with elements incidents – Version 1 – tenant-a",
    });

    expect(tenantProcess).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=process-elements-incidents&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidentErrorHashCode=234254&incidents=true&tenant=tenant-a`,
    );
  });

  it('should truncate the error message in search params', async () => {
    mockIncidentQueries({
      incidents: mockIncidentStatisticsByErrorWithBigMessage,
    });

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const truncated = truncateErrorMessage(bigErrorMessage);
    const mainSearch = new URLSearchParams({
      errorMessage: truncated,
      incidentErrorHashCode: '234254',
      incidents: 'true',
    }).toString();

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0'),
    );

    const expandButton = withinIncident.getByRole('button', {
      name: 'Expand current row',
    });

    expect(
      withinIncident.getByRole('link', {
        description: `View 36 Instances with error ${bigErrorMessage}`,
      }),
    ).toHaveAttribute('href', `${Paths.processes()}?${mainSearch}`);

    await user.click(expandButton);

    const firstProcess = await screen.findByRole('link', {
      description: `View 52 Instances with error ${bigErrorMessage} in version 1 of Process Call Level 2 Process – Version 1`,
    });

    const detailSearch = new URLSearchParams({
      process: 'call-level-2-process',
      version: '1',
      errorMessage: truncated,
      incidentErrorHashCode: '234254',
      incidents: 'true',
    }).toString();

    expect(firstProcess).toHaveAttribute(
      'href',
      `${Paths.processes()}?${detailSearch}`,
    );
  });

  it('should expand filters panel on click', async () => {
    mockIncidentQueries();

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0'),
    );

    const expandButton = withinIncident.getByRole('button', {
      name: 'Expand current row',
    });

    await user.click(expandButton);

    const processLink = await screen.findByRole('link', {
      description:
        "View 52 Instances with error JSON path '$.paid' has no result. in version 1 of Process Call Level 2 Process – Version 1",
    });

    panelStatesStore.toggleFiltersPanel();
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(processLink);

    await waitFor(() =>
      expect(panelStatesStore.state.isFiltersCollapsed).toBe(false),
    );
  });
});
