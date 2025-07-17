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
  mockIncidentsByErrorWithBigErrorMessage,
  bigErrorMessage,
} from './index.setup';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchIncidentsByError} from 'modules/mocks/api/incidents/fetchIncidentsByError';
import {Paths} from 'modules/Routes';
import {incidentsByErrorStore} from 'modules/stores/incidentsByError';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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

  it('should display skeleton when loading', async () => {
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );
  });

  it('should handle server errors', async () => {
    mockFetchIncidentsByError().withServerError();

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

    mockFetchIncidentsByError().withNetworkError();

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should display information message when there are no processes', async () => {
    mockFetchIncidentsByError().withSuccess([]);

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

  it('should render process incidents by error message', async () => {
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);

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
        "View 36 Instances with error JSON path '$.paid' has no result.",
    });

    expect(processLink).toHaveAttribute(
      'href',
      '/processes?errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidentErrorHashCode=234254&incidents=true',
    );

    // this button click has no effect (check useEffect in Collapse component)
    await user.click(expandButton);

    const firstVersion = await screen.findByRole('link', {
      description:
        "View 37 Instances with error JSON path '$.paid' has no result. in version 1 of Process mockProcess",
    });
    expect(
      within(firstVersion).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByText('mockProcess – Version 1'),
    ).toBeInTheDocument();

    expect(firstVersion).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidentErrorHashCode=234254&incidents=true',
    );
  });

  it('should update after next poll', async () => {
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0'),
    );

    expect(
      withinIncident.getByRole('button', {
        name: 'Expand current row',
      }),
    ).toBeInTheDocument();

    mockFetchIncidentsByError().withSuccess([
      {...mockIncidentsByError[0]!, instancesWithErrorCount: 40},
      mockIncidentsByError[1]!,
    ]);

    expect(
      await withinIncident.findByRole('button', {
        name: 'Expand current row',
      }),
    ).toBeInTheDocument();

    await waitFor(() =>
      expect(incidentsByErrorStore.isPollRequestRunning).toBe(false),
    );
  });

  it('should truncate the error message search param', async () => {
    mockFetchIncidentsByError().withSuccess(
      mockIncidentsByErrorWithBigErrorMessage,
    );

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

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
    ).toHaveAttribute(
      'href',
      '/processes?errorMessage=Lorem+ipsum+dolor+sit+amet%2C+consectetur+adipiscing+elit%2C+sed+do+eiusmod+tempor+incididunt+ut+labore&incidentErrorHashCode=234254&incidents=true',
    );

    // this button click has no effect (check useEffect in Collapse component)
    await user.click(expandButton);

    expect(
      await screen.findByRole('link', {
        description: `View 37 Instances with error ${bigErrorMessage} in version 1 of Process mockProcess`,
      }),
    ).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=1&errorMessage=Lorem+ipsum+dolor+sit+amet%2C+consectetur+adipiscing+elit%2C+sed+do+eiusmod+tempor+incididunt+ut+labore&incidentErrorHashCode=234254&incidents=true',
    );
  });

  it('should expand filters panel on click', async () => {
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);

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
        "View 36 Instances with error JSON path '$.paid' has no result.",
    });

    await user.click(processLink);

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?errorMessage=JSON\+path\+%27%24.paid%27\+has\+no\+result.&incidentErrorHashCode=234254&incidents=true$/,
      ),
    );
  });
});
