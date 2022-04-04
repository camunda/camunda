/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {Route, MemoryRouter, Routes, Link} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Instances} from './index';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
  mockProcessInstances,
  operations,
} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import userEvent from '@testing-library/user-event';
import {instancesStore} from 'modules/stores/instances';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {processStatisticsStore} from 'modules/stores/processStatistics';
import {operationsStore} from 'modules/stores/operations';
import {processesStore} from 'modules/stores/processes';
import {LocationLog} from 'modules/utils/LocationLog';

jest.mock('modules/utils/bpmn');

function getWrapper(initialPath: string = '/processes') {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/processes" element={children} />
          </Routes>
          <Link to="/processes?active=true">go to active</Link>
          <Link to="/processes?process=eventBasedGatewayProcess&version=1">
            go to event based
          </Link>
          <Link to="/processes">go to no filters</Link>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('Instances', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedProcessesMock))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
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
    processStatisticsStore.reset();
    operationsStore.reset();
    processesStore.reset();
  });

  it('should render title and document title', () => {
    render(<Instances />, {
      wrapper: getWrapper('/processes?incidents=true&active=true'),
    });

    expect(screen.getByText('Operate Instances')).toBeInTheDocument();
    expect(document.title).toBe('Operate: Instances');
  });

  it('should render page components', async () => {
    render(<Instances />, {
      wrapper: getWrapper('/processes?active=true&incidents=true'),
    });

    // diagram panel
    expect(screen.getByRole('heading', {name: 'Process'})).toBeInTheDocument();
    expect(
      screen.getByText('There is no Process selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel'
      )
    ).toBeInTheDocument();

    // filters panel
    expect(screen.getByRole('heading', {name: /Filters/})).toBeInTheDocument();

    // instances table
    expect(
      await screen.findByRole('heading', {name: /instances 912 results found/i})
    ).toBeInTheDocument();

    // operations
    expect(
      screen.getByRole('button', {name: /expand operations/i})
    ).toBeInTheDocument();
  });

  it('should reset selected instances when filters change', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    render(<Instances />, {
      wrapper: getWrapper('/processes?active=true&incidents=true'),
    });

    expect(instanceSelectionStore.state).toEqual({
      selectedInstanceIds: [],
      isAllChecked: false,
      selectionMode: 'INCLUDE',
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

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

    userEvent.click(screen.getByText(/go to active/i));

    await waitFor(() =>
      expect(instanceSelectionStore.state).toEqual({
        selectedInstanceIds: [],
        isAllChecked: false,
        selectionMode: 'INCLUDE',
      })
    );
  });

  it('should fetch diagram and diagram statistics', async () => {
    const firstProcessStatisticsResponse = [
      {
        activityId: 'ServiceTask_0kt6c5i',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 10,
      },
    ];
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(firstProcessStatisticsResponse))
      )
    );

    render(<Instances />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=1'),
    });

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetched')
    );
    await waitFor(() =>
      expect(processStatisticsStore.state.isLoading).toBe(false)
    );
    expect(instancesDiagramStore.state.diagramModel).not.toBe(null);
    expect(processStatisticsStore.state.statistics).toEqual(
      firstProcessStatisticsResponse
    );

    userEvent.click(screen.getByText(/go to event based/i));

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetching')
    );
    await waitFor(() =>
      expect(processStatisticsStore.state.isLoading).toBe(true)
    );

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetched')
    );
    expect(instancesDiagramStore.state.diagramModel).not.toBe(null);
    expect(processStatisticsStore.state.statistics).toEqual(
      mockProcessStatistics
    );

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({processInstances: []}))
      )
    );

    userEvent.click(screen.getByText(/go to no filters/i));

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual([])
    );
  });
});
