/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {Router, Route} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Instances} from './index';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowXML,
  mockWorkflowInstances,
  operations,
} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import userEvent from '@testing-library/user-event';
import {instancesStore} from 'modules/stores/instances';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {workflowStatisticsStore} from 'modules/stores/workflowStatistics';
import {operationsStore} from 'modules/stores/operations';
import {workflowsStore} from 'modules/stores/workflows';

jest.mock('modules/utils/bpmn');

function getWrapper(
  history = createMemoryHistory({
    initialEntries: ['/instances'],
  })
) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <CollapsablePanelProvider>
          <Router history={history}>
            <Route path="/instances">{children} </Route>
          </Router>
        </CollapsablePanelProvider>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('Instances', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      ),
      rest.post('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            coreStatistics: {
              running: 821,
              active: 90,
              withIncidents: 731,
            },
          })
        )
      ),
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );
  });

  afterEach(() => {
    instanceSelectionStore.reset();
    instancesStore.reset();
    instancesDiagramStore.reset();
    workflowStatisticsStore.reset();
    operationsStore.reset();
    workflowsStore.reset();
  });

  it('should render title and document title', () => {
    render(<Instances />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?incidents=true&active=true'],
        })
      ),
    });

    expect(screen.getByText('Camunda Operate Instances')).toBeInTheDocument();
    expect(document.title).toBe('Camunda Operate: Instances');
  });

  it('should render page components', () => {
    render(<Instances />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?active=true&incidents=true'],
        })
      ),
    });

    // diagram panel
    expect(screen.getByRole('heading', {name: 'Workflow'})).toBeInTheDocument();
    expect(
      screen.getByText('There is no Workflow selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Workflow in the Filters panel'
      )
    ).toBeInTheDocument();

    // filters panel
    expect(screen.getByRole('heading', {name: /Filters/})).toBeInTheDocument();

    // instances table
    expect(
      screen.getByRole('heading', {name: 'Instances'})
    ).toBeInTheDocument();

    // operations
    expect(
      screen.getByRole('button', {name: /expand operations/i})
    ).toBeInTheDocument();
  });

  it('should reset selected instances when filters change', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/instances?active=true&incidents=true'],
    });
    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowInstances))
      )
    );

    render(<Instances />, {
      wrapper: getWrapper(mockHistory),
    });

    expect(instanceSelectionStore.state).toEqual({
      selectedInstanceIds: [],
      isAllChecked: false,
      selectionMode: 'INCLUDE',
    });

    await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

    userEvent.click(
      await screen.findByRole('checkbox', {
        name: /select instance 2251799813685594/i,
      })
    );

    expect(instanceSelectionStore.state).toEqual({
      selectedInstanceIds: ['2251799813685594'],
      isAllChecked: false,
      selectionMode: 'INCLUDE',
    });

    mockHistory.push('/instances?active=true');

    await waitFor(() =>
      expect(instanceSelectionStore.state).toEqual({
        selectedInstanceIds: [],
        isAllChecked: false,
        selectionMode: 'INCLUDE',
      })
    );
  });

  it('should fetch diagram and diagram statistics', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/instances?workflow=bigVarProcess&version=1'],
    });
    const firstWorkflowStatisticsResponse = [
      {
        activityId: 'ServiceTask_0kt6c5i',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 10,
      },
    ];
    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(firstWorkflowStatisticsResponse))
      )
    );

    render(<Instances />, {
      wrapper: getWrapper(mockHistory),
    });

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetched')
    );
    await waitFor(() =>
      expect(workflowStatisticsStore.state.isLoading).toBe(false)
    );
    expect(instancesDiagramStore.state.diagramModel).not.toBe(null);
    expect(workflowStatisticsStore.state.statistics).toEqual(
      firstWorkflowStatisticsResponse
    );

    mockHistory.push('/instances?workflow=eventBasedGatewayProcess&version=1');

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetching')
    );
    await waitFor(() =>
      expect(workflowStatisticsStore.state.isLoading).toBe(true)
    );

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetched')
    );
    expect(instancesDiagramStore.state.diagramModel).not.toBe(null);
    expect(workflowStatisticsStore.state.statistics).toEqual(
      mockWorkflowStatistics
    );

    mockHistory.push('/instances');

    mockServer.use(
      rest.post('/api/workflow-instances', (_, res, ctx) =>
        res.once(ctx.json({workflowInstances: []}))
      )
    );

    await waitFor(() =>
      expect(workflowStatisticsStore.state.statistics).toEqual([])
    );
  });
});
