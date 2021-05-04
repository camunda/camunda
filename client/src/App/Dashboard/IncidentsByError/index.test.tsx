/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Router} from 'react-router-dom';
import {
  render,
  within,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {createMemoryHistory} from 'history';
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

function getParam(search: string, param: string) {
  return new URLSearchParams(search).get(param);
}

const createWrapper = (historyMock = createMemoryHistory()) => ({
  children,
}: any) => (
  <ThemeProvider>
    <Router history={historyMock}>{children}</Router>
  </ThemeProvider>
);

describe('IncidentsByError', () => {
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
      await screen.findByText('Incidents by Error Message could not be fetched')
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
      await screen.findByText('Incidents by Error Message could not be fetched')
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
      await screen.findByText('There are no Instances with Incidents')
    ).toBeInTheDocument();
  });

  it('should render incidents by error message', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );

    const historyMock = createMemoryHistory();
    render(<IncidentsByError />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byError-0')
    );

    const expandButton = withinIncident.getByTitle(
      "Expand 36 Instances with error JSON path '$.paid' has no result."
    );
    expect(expandButton).toBeInTheDocument();

    userEvent.click(
      withinIncident.getByTitle(
        "View 36 Instances with error JSON path '$.paid' has no result."
      )
    );
    expect(historyMock.location.search).toBe(
      '?errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );

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
    expect(historyMock.location.search).toBe(
      '?process=mockProcess&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );
  });

  it('should not erase persistent params', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      )
    );

    const historyMock = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });

    render(<IncidentsByError />, {
      wrapper: createWrapper(historyMock),
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
    expect(historyMock.location.search).toBe(
      `?gseUrl=https%3A%2F%2Fwww.testUrl.com&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true`
    );

    userEvent.click(expandButton);

    const firstVersion = withinIncident.getByTitle(
      "View 37 Instances with error JSON path '$.paid' has no result. in version 1 of Process mockProcess"
    );

    userEvent.click(firstVersion);
    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&process=mockProcess&version=1&errorMessage=JSON+path+%27%24.paid%27+has+no+result.&incidents=true'
    );
  });

  it('should truncate the error message search param', async () => {
    mockServer.use(
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByErrorWithBigErrorMessage))
      )
    );

    const historyMock = createMemoryHistory({
      initialEntries: ['/'],
    });

    render(<IncidentsByError />, {
      wrapper: createWrapper(historyMock),
    });

    userEvent.click(
      await screen.findByTitle(
        `View 36 Instances with error ${bigErrorMessage}`
      )
    );

    expect(getParam(historyMock.location.search, 'errorMessage')).toBe(
      truncatedBigErrorMessage
    );

    historyMock.push('/');

    expect(getParam(historyMock.location.search, 'errorMessage')).toBeNull();

    userEvent.click(screen.getByTestId('arrow-icon'));
    userEvent.click(
      await screen.findByTitle(
        `View 37 Instances with error ${bigErrorMessage} in version 1 of Process mockProcess`
      )
    );

    expect(getParam(historyMock.location.search, 'errorMessage')).toBe(
      truncatedBigErrorMessage
    );
  });
});
