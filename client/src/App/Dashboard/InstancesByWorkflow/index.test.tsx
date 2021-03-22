/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';
import {render, fireEvent, within, screen} from '@testing-library/react';
import {createMemoryHistory} from 'history';
import {InstancesByWorkflow} from './index';
import {
  mockWithSingleVersion,
  mockErrorResponse,
  mockEmptyResponse,
  mockWithMultipleVersions,
} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

const createWrapper = (historyMock = createMemoryHistory()) => ({
  children,
}: any) => (
  <ThemeProvider>
    <Router history={historyMock}>{children}</Router>
  </ThemeProvider>
);

describe('InstancesByWorkflow', () => {
  it('should display skeleton when loading', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    render(<InstancesByWorkflow />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    expect(
      await screen.findByTestId('instances-by-workflow')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
  });

  it('should handle server errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json(mockErrorResponse))
      )
    );

    render(<InstancesByWorkflow />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Instances by Workflow could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle network errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );

    render(<InstancesByWorkflow />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Instances by Workflow could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display information message when there are no workflows', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockEmptyResponse))
      )
    );

    render(<InstancesByWorkflow />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('There are no Workflows deployed')
    ).toBeInTheDocument();
  });

  it('should render items with more than one workflows versions', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockWithMultipleVersions))
      )
    );

    const historyMock = createMemoryHistory();
    render(<InstancesByWorkflow />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byWorkflow-0')
    );

    const workflowLink = withinIncident.getByText(
      'Order process – 201 Instances in 2 Versions'
    );
    expect(workflowLink).toBeInTheDocument();
    fireEvent.click(workflowLink);
    expect(historyMock.location.search).toBe(
      '?workflow=orderProcess&version=all&active=true&incidents=true'
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '65'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '136'
    );

    const expandButton = withinIncident.getByTitle(
      'Expand 201 Instances of Workflow Order process'
    );

    expect(expandButton).toBeInTheDocument();
    fireEvent.click(expandButton);

    const firstVersion = screen.getByTitle(
      'View 42 Instances in Version 1 of Workflow First Version'
    );

    expect(
      within(firstVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(firstVersion).getByText(
        'First Version – 42 Instances in Version 1'
      )
    ).toBeInTheDocument();

    fireEvent.click(firstVersion);
    expect(historyMock.location.search).toBe(
      '?workflow=mockWorkflow&version=1&active=true&incidents=true'
    );

    const secondVersion = screen.getByTitle(
      'View 42 Instances in Version 2 of Workflow Second Version'
    );

    expect(
      within(secondVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(secondVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(secondVersion).getByText(
        'Second Version – 42 Instances in Version 2'
      )
    ).toBeInTheDocument();

    fireEvent.click(secondVersion);
    expect(historyMock.location.search).toBe(
      '?workflow=mockWorkflow&version=2&active=true&incidents=true'
    );
  });

  it('should render items with one workflow version', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    const historyMock = createMemoryHistory();
    render(<InstancesByWorkflow />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byWorkflow-0')
    );

    expect(
      withinIncident.queryByTestId('expand-button')
    ).not.toBeInTheDocument();

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
    ).toBeInTheDocument();

    const workflowLink = withinIncident.getByTitle(
      'View 138 Instances in 1 Version of Workflow loanProcess'
    );
    expect(workflowLink).toBeInTheDocument();
    fireEvent.click(workflowLink);
    expect(historyMock.location.search).toBe(
      '?workflow=loanProcess&version=1&active=true&incidents=true'
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '16'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '122'
    );
  });

  it('should not erase persistent params', async () => {
    mockServer.use(
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockWithMultipleVersions))
      )
    );

    const historyMock = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });

    render(<InstancesByWorkflow />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byWorkflow-0')
    );

    const workflowLink = withinIncident.getByText(
      'Order process – 201 Instances in 2 Versions'
    );

    fireEvent.click(workflowLink);
    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&workflow=orderProcess&version=all&active=true&incidents=true'
    );

    fireEvent.click(
      withinIncident.getByTitle(
        'Expand 201 Instances of Workflow Order process'
      )
    );

    fireEvent.click(
      screen.getByTitle(
        'View 42 Instances in Version 1 of Workflow First Version'
      )
    );

    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&workflow=mockWorkflow&version=1&active=true&incidents=true'
    );
  });
});
