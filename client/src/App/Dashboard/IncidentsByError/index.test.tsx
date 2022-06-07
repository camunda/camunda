/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  within,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {IncidentsByError} from './index';
import {
  mockIncidentsByError,
  mockErrorResponse,
  mockIncidentsByErrorWithBigErrorMessage,
  bigErrorMessage,
} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/processes" element={<div>Processes</div>} />
            <Route path="/" element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
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
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));
  });

  it('should handle server errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json(mockErrorResponse))
      )
    );

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle network errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res) =>
        res.networkError('A network error')
      )
    );

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display information message when there are no processes', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('There are no Process Instances with Incidents')
    ).toBeInTheDocument();
  });

  it('should render process incidents by error message', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0')
    );

    const expandButton = withinIncident.getByTitle(
      "Expand 36 Instances with error JSON path '$.paid' has no result."
    );
    expect(expandButton).toBeInTheDocument();

    const processLink = withinIncident.getByRole('link', {
      name: "View 36 Instances with error JSON path '$.paid' has no result.",
    });

    expect(processLink).toHaveAttribute(
      'href',
      '/processes?errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );

    await user.click(expandButton);

    const firstVersion = withinIncident.getByRole('link', {
      name: "View 37 Instances with error JSON path '$.paid' has no result. in version 1 of Process mockProcess",
    });
    expect(
      within(firstVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByText('mockProcess – Version 1')
    ).toBeInTheDocument();

    expect(firstVersion).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );
  });

  it('should update after next poll', async () => {
    jest.useFakeTimers();

    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0')
    );

    expect(
      withinIncident.getByTitle(
        "Expand 36 Instances with error JSON path '$.paid' has no result."
      )
    ).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(
          ctx.json([
            {...mockIncidentsByError[0], instancesWithErrorCount: 40},
            mockIncidentsByError[1],
          ])
        )
      )
    );

    jest.runOnlyPendingTimers();

    expect(
      await withinIncident.findByTitle(
        "Expand 40 Instances with error JSON path '$.paid' has no result."
      )
    ).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should truncate the error message search param', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByErrorWithBigErrorMessage))
      )
    );

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0')
    );

    const expandButton = withinIncident.getByTitle(
      `Expand 36 Instances with error ${bigErrorMessage}`
    );

    expect(
      withinIncident.getByRole('link', {
        name: `View 36 Instances with error ${bigErrorMessage}`,
      })
    ).toHaveAttribute(
      'href',
      '/processes?errorMessage=Lorem+ipsum+dolor+sit+amet%2C+consectetur+adipiscing+elit%2C+sed+do+eiusmod+tempor+incididunt+ut+labore&incidents=true'
    );

    await user.click(expandButton);

    expect(
      screen.getByRole('link', {
        name: `View 37 Instances with error ${bigErrorMessage} in version 1 of Process mockProcess`,
      })
    ).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=1&errorMessage=Lorem+ipsum+dolor+sit+amet%2C+consectetur+adipiscing+elit%2C+sed+do+eiusmod+tempor+incididunt+ut+labore&incidents=true'
    );
  });

  it('should expand filters panel on click', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );

    const {user} = render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0')
    );

    const expandButton = withinIncident.getByTitle(
      "Expand 36 Instances with error JSON path '$.paid' has no result."
    );
    expect(expandButton).toBeInTheDocument();

    const processLink = withinIncident.getByRole('link', {
      name: "View 36 Instances with error JSON path '$.paid' has no result.",
    });
    await user.click(processLink);

    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?errorMessage=JSON\+path\+%27%24.paid%27\+has\+no\+result.&incidents=true$/
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });
});
