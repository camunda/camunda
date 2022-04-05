/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Link, MemoryRouter} from 'react-router-dom';
import {
  render,
  within,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {IncidentsByError} from './index';
import {
  mockIncidentsByError,
  mockErrorResponse,
  mockEmptyResponse,
  mockIncidentsByErrorWithBigErrorMessage,
  bigErrorMessage,
  truncatedBigErrorMessage,
} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';

function getParam(search: string, param: string) {
  return new URLSearchParams(search).get(param);
}

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <Link to="/">go to initial</Link>
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
        res.once(ctx.json(mockEmptyResponse))
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

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0')
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    const expandButton = withinIncident.getByTitle(
      "Expand 36 Instances with error JSON path '$.paid' has no result."
    );
    expect(expandButton).toBeInTheDocument();

    userEvent.click(
      withinIncident.getByTitle(
        "View 36 Instances with error JSON path '$.paid' has no result."
      )
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
    panelStatesStore.toggleFiltersPanel();
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    userEvent.click(expandButton);

    const firstVersion = withinIncident.getByTitle(
      "View 37 Instances with error JSON path '$.paid' has no result. in version 1 of Process mockProcess"
    );
    expect(
      within(firstVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByText('mockProcess – Version 1')
    ).toBeInTheDocument();

    userEvent.click(firstVersion);
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?process=mockProcess&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
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

  it('should not erase persistent params', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );

    render(<IncidentsByError />, {
      wrapper: createWrapper('/?gseUrl=https://www.testUrl.com'),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0')
    );

    const expandButton = withinIncident.getByTitle(
      "Expand 36 Instances with error JSON path '$.paid' has no result."
    );

    userEvent.click(
      withinIncident.getByTitle(
        "View 36 Instances with error JSON path '$.paid' has no result."
      )
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      `?gseUrl=https%3A%2F%2Fwww.testUrl.com&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true`
    );

    userEvent.click(expandButton);

    const firstVersion = withinIncident.getByTitle(
      "View 37 Instances with error JSON path '$.paid' has no result. in version 1 of Process mockProcess"
    );

    userEvent.click(firstVersion);
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&process=mockProcess&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );
  });

  it('should truncate the error message search param', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByErrorWithBigErrorMessage))
      )
    );

    render(<IncidentsByError />, {
      wrapper: createWrapper(),
    });

    userEvent.click(
      await screen.findByTitle(
        `View 36 Instances with error ${bigErrorMessage}`
      )
    );

    expect(
      getParam(screen.getByTestId('search').textContent ?? '', 'errorMessage')
    ).toBe(truncatedBigErrorMessage);

    userEvent.click(screen.getByText(/go to initial/i));

    expect(
      // eslint-disable-next-line testing-library/prefer-presence-queries
      getParam(screen.getByTestId('search').textContent ?? '', 'errorMessage')
    ).toBeNull();

    userEvent.click(screen.getByTestId('arrow-icon'));
    userEvent.click(
      await screen.findByTitle(
        `View 37 Instances with error ${bigErrorMessage} in version 1 of Process mockProcess`
      )
    );

    expect(
      getParam(screen.getByTestId('search').textContent ?? '', 'errorMessage')
    ).toBe(truncatedBigErrorMessage);
  });
});
