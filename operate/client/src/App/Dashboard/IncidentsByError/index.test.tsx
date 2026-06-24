/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, within, screen, waitFor} from 'modules/testing-library';
import {IncidentsByError} from './index';
import {
  mockIncidentsByError,
  mockIncidentStatisticsByErrorWithBigMessage,
  mockIncidentStatisticsByDefinition,
  bigErrorMessage,
} from './index.setup';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';
import {mockFetchIncidentProcessInstanceStatisticsByDefinition} from 'modules/mocks/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByDefinition';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser} from 'modules/testUtils';
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

const mockScrollableContainerRef = {current: null};
const mockScrollHandlers = {
  onScrollStartReach: () => {},
  onScrollEndReach: () => {},
};

describe('IncidentsByError', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser());
  });

  const mockDefinitionQueries = ({
    definitions = mockIncidentStatisticsByDefinition,
  } = {}) => {
    mockFetchIncidentProcessInstanceStatisticsByDefinition().withSuccess(
      definitions,
    );
    mockFetchIncidentProcessInstanceStatisticsByDefinition().withSuccess(
      definitions,
    );
  };

  it('should display skeleton when loading', () => {
    render(
      <IncidentsByError
        status="pending"
        items={[]}
        totalCount={0}
        scrollableContainerRef={mockScrollableContainerRef}
        isFetchingNextPage={false}
        isFetchingPreviousPage={false}
        {...mockScrollHandlers}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should handle errors', () => {
    render(
      <IncidentsByError
        status="error"
        items={[]}
        totalCount={0}
        scrollableContainerRef={mockScrollableContainerRef}
        isFetchingNextPage={false}
        isFetchingPreviousPage={false}
        {...mockScrollHandlers}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
  });

  it('should display information message when there are no incidents', () => {
    render(
      <IncidentsByError
        status="success"
        items={[]}
        totalCount={0}
        scrollableContainerRef={mockScrollableContainerRef}
        isFetchingNextPage={false}
        isFetchingPreviousPage={false}
        {...mockScrollHandlers}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('Your processes are healthy')).toBeInTheDocument();
    expect(
      screen.getByText('There are no incidents on any instances.'),
    ).toBeInTheDocument();
  });

  it('should render process incidents with expandable process list', async () => {
    mockDefinitionQueries();

    const {user} = render(
      <IncidentsByError
        status="success"
        items={mockIncidentsByError.items}
        totalCount={mockIncidentsByError.page.totalItems}
        scrollableContainerRef={mockScrollableContainerRef}
        isFetchingNextPage={false}
        isFetchingPreviousPage={false}
        {...mockScrollHandlers}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const withinIncident = within(screen.getByTestId('incident-byError-0'));

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
      `${Paths.processes()}?processDefinitionId=call-level-2-process&processDefinitionVersion=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidentErrorHashCode=234254&incidents=true`,
    );
  });

  it('should include tenant in link when multi-tenancy is enabled', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });

    mockDefinitionQueries();

    const {user} = render(
      <IncidentsByError
        status="success"
        items={mockIncidentsByError.items}
        totalCount={mockIncidentsByError.page.totalItems}
        scrollableContainerRef={mockScrollableContainerRef}
        isFetchingNextPage={false}
        isFetchingPreviousPage={false}
        {...mockScrollHandlers}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const expandButton = within(
      screen.getByTestId('incident-byError-0'),
    ).getByRole('button', {name: 'Expand current row'});

    await user.click(expandButton);

    const tenantProcess = await screen.findByRole('link', {
      description:
        "View 26 Instances with error JSON path '$.paid' has no result. in version 1 of Process Process with elements incidents – Version 1 – tenant-a",
    });

    expect(tenantProcess).toHaveAttribute(
      'href',
      `${Paths.processes()}?processDefinitionId=process-elements-incidents&processDefinitionVersion=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidentErrorHashCode=234254&incidents=true&tenantId=tenant-a`,
    );
  });

  it('should truncate the error message in search params', async () => {
    mockDefinitionQueries();

    const {user} = render(
      <IncidentsByError
        status="success"
        items={mockIncidentStatisticsByErrorWithBigMessage.items}
        totalCount={mockIncidentStatisticsByErrorWithBigMessage.page.totalItems}
        scrollableContainerRef={mockScrollableContainerRef}
        isFetchingNextPage={false}
        isFetchingPreviousPage={false}
        {...mockScrollHandlers}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const truncated = truncateErrorMessage(bigErrorMessage);
    const mainSearch = new URLSearchParams({
      errorMessage: truncated,
      incidentErrorHashCode: '234254',
      incidents: 'true',
    }).toString();

    const withinIncident = within(screen.getByTestId('incident-byError-0'));

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
      processDefinitionId: 'call-level-2-process',
      processDefinitionVersion: '1',
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
    mockDefinitionQueries();

    const {user} = render(
      <IncidentsByError
        status="success"
        items={mockIncidentsByError.items}
        totalCount={mockIncidentsByError.page.totalItems}
        scrollableContainerRef={mockScrollableContainerRef}
        isFetchingNextPage={false}
        isFetchingPreviousPage={false}
        {...mockScrollHandlers}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);

    const withinIncident = within(screen.getByTestId('incident-byError-0'));

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
