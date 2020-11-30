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

import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {filtersStore} from 'modules/stores/filters';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/utils/bpmn');

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <CollapsablePanelProvider>{children}</CollapsablePanelProvider>
    </ThemeProvider>
  );
};

describe('DiagramPanel', () => {
  const historyMock = createMemoryHistory();

  beforeEach(() => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
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
  });

  afterEach(() => {
    instancesDiagramStore.reset();
    filtersStore.reset();
  });

  it('should render header', async () => {
    const locationMock = {
      pathname: '/instances',
      search:
        '?filter={%22active%22:true,%22incidents%22:true,%22version%22:%221%22,%22workflow%22:%22bigVarProcess%22}&name=%22Big%20variable%20process%22',
    };
    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();

    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Big variable process')).toBeInTheDocument();
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    const locationMock = {
      pathname: '/instances',
    };
    filtersStore.setUrlParameters(historyMock, locationMock);
    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });
    instancesDiagramStore.fetchWorkflowXml('1');

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    expect(screen.getByTestId('diagram')).toBeInTheDocument();
  });

  it('should show an empty state message when no workflow is selected', async () => {
    const locationMock = {
      pathname: '/instances',
      search: '?filter={%22active%22:true,%22incidents%22:true}',
    };
    filtersStore.setUrlParameters(historyMock, locationMock);

    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });

    await filtersStore.init();

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
    const locationMock = {
      pathname: '/instances',
      search:
        '?filter={%22active%22:true,%22incidents%22:true,%22version%22:%22all%22,%22workflow%22:%22bigVarProcess%22}&name=%22Big%20variable%20process%22',
    };
    filtersStore.setUrlParameters(historyMock, locationMock);

    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });
    await filtersStore.init();

    expect(
      screen.getByText(
        'There is more than one Version selected for Workflow "Big variable process"'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a Diagram, select a single Version')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res) =>
        res.networkError('A network error')
      )
    );

    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
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
