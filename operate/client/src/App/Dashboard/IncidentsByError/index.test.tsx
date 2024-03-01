/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchIncidentsByError} from 'modules/mocks/api/incidents/fetchIncidentsByError';
import {Paths} from 'modules/Routes';
import {incidentsByErrorStore} from 'modules/stores/incidentsByError';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.processes()} element={<div>Processes</div>} />
          <Route path={Paths.dashboard()} element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('IncidentsByError', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
  });

  afterEach(() => {
    panelStatesStore.reset();
  });

  it('should display skeleton when loading', async () => {
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));
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
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

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
      '/processes?errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true',
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
      '/processes?process=mockProcess&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true',
    );
  });

  it('should update after next poll', async () => {
    jest.useFakeTimers();

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

    jest.runOnlyPendingTimers();

    expect(
      await withinIncident.findByRole('button', {
        name: 'Expand current row',
      }),
    ).toBeInTheDocument();

    await waitFor(() =>
      expect(incidentsByErrorStore.isPollRequestRunning).toBe(false),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
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
      '/processes?errorMessage=Lorem+ipsum+dolor+sit+amet%2C+consectetur+adipiscing+elit%2C+sed+do+eiusmod+tempor+incididunt+ut+labore&incidents=true',
    );

    // this button click has no effect (check useEffect in Collapse component)
    await user.click(expandButton);

    expect(
      await screen.findByRole('link', {
        description: `View 37 Instances with error ${bigErrorMessage} in version 1 of Process mockProcess`,
      }),
    ).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=1&errorMessage=Lorem+ipsum+dolor+sit+amet%2C+consectetur+adipiscing+elit%2C+sed+do+eiusmod+tempor+incididunt+ut+labore&incidents=true',
    );
  });

  it('should expand filters panel on click', async () => {
    jest.useFakeTimers();
    mockFetchIncidentsByError().withSuccess(mockIncidentsByError);

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

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
        /^\?errorMessage=JSON\+path\+%27%24.paid%27\+has\+no\+result.&incidents=true$/,
      ),
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
