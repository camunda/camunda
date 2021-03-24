/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {Router} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {createMemoryHistory} from 'history';
import {mockProps} from './index.setup';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {DiagramPanel} from './index';
import {workflowsStore} from 'modules/stores/workflows';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

jest.mock('modules/utils/bpmn');

function getWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <Router history={history}>
        <ThemeProvider>
          <CollapsablePanelProvider>{children}</CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    workflowsStore.fetchWorkflows();
  });

  afterEach(() => {
    instancesDiagramStore.reset();
    workflowsStore.reset();
  });

  it('should render header', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?workflow=bigVarProcess&version=1'],
        })
      ),
    });

    expect(await screen.findByText('Big variable process')).toBeInTheDocument();
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(),
    });
    instancesDiagramStore.fetchWorkflowXml('1');

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    expect(screen.getByTestId('diagram')).toBeInTheDocument();
  });

  it('should show an empty state message when no workflow is selected', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByText('There is no Workflow selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Workflow in the Filters panel'
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show a message when no workflow version is selected', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?workflow=bigVarProcess&version=all'],
        })
      ),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Workflow "Big variable process"'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a Diagram, select a single Version')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should display bpmnProcessId as workflow name in the message when no workflow version is selected', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: [
            '/instances?workflow=eventBasedGatewayProcess&version=all',
          ],
        })
      ),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Workflow "eventBasedGatewayProcess"'
      )
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res) =>
        res.networkError('A network error')
      )
    );

    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(),
    });

    instancesDiagramStore.fetchWorkflowXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    instancesDiagramStore.fetchWorkflowXml('1');

    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    expect(
      screen.queryByText('Diagram could not be fetched')
    ).not.toBeInTheDocument();

    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''), ctx.status(500))
      )
    );

    instancesDiagramStore.fetchWorkflowXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });
});
