/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {Route, MemoryRouter, Routes, Link} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Processes} from '../index';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
  mockProcessInstances,
} from 'modules/testUtils';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {processesStore} from 'modules/stores/processes';
import {LocationLog} from 'modules/utils/LocationLog';
import {AppHeader} from 'App/Layout/AppHeader';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';

jest.mock('modules/utils/bpmn');

function getWrapper(initialPath: string = '/processes') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesSelectionStore.reset();
        processInstancesStore.reset();
        processDiagramStore.reset();
        processesStore.reset();
      };
    }, []);
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
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);
  });

  it('should render title and document title', () => {
    render(<Processes />, {
      wrapper: getWrapper('/processes?incidents=true&active=true'),
    });

    expect(screen.getByText('Operate Process Instances')).toBeInTheDocument();
    expect(document.title).toBe('Operate: Process Instances');
  });

  it('should render page components', async () => {
    render(<Processes />, {
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
      screen.getByRole('heading', {name: /^process instances$/i})
    ).toBeInTheDocument();
    expect(await screen.findByText(/^912 results found$/i)).toBeInTheDocument();
  });

  it('should reset selected instances when filters change', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<Processes />, {
      wrapper: getWrapper('/processes?active=true&incidents=true'),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByLabelText(/select instance 2251799813685594/i)
    ).not.toBeChecked();

    await user.click(
      await screen.findByRole('checkbox', {
        name: /select instance 2251799813685594/i,
      })
    );
    expect(
      screen.getByLabelText(/select instance 2251799813685594/i)
    ).toBeChecked();

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    await user.click(screen.getByText(/go to active/i));
    await waitForElementToBeRemoved(screen.getByTestId('instances-loader'));

    expect(
      screen.getByLabelText(/select instance 2251799813685594/i)
    ).not.toBeChecked();
  });

  it('should not reset selected instances when table is sorted', async () => {
    const {user} = render(<Processes />, {
      wrapper: getWrapper('/processes?active=true&incidents=true'),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByLabelText(/select instance 2251799813685594/i)
    ).not.toBeChecked();

    await user.click(
      await screen.findByRole('checkbox', {
        name: /select instance 2251799813685594/i,
      })
    );
    expect(
      screen.getByLabelText(/select instance 2251799813685594/i)
    ).toBeChecked();

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    expect(await screen.findByTestId('instances-loader')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('instances-loader'));

    expect(
      screen.getByLabelText(/select instance 2251799813685594/i)
    ).toBeChecked();
  });

  it('should fetch diagram and diagram statistics', async () => {
    const firstProcessStatisticsResponse = [
      {...mockProcessStatistics[0]!, completed: 10},
    ];

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(
      firstProcessStatisticsResponse
    );

    const {user} = render(<Processes />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=1'),
    });

    await waitFor(() =>
      expect(processDiagramStore.state.status).toBe('fetched')
    );

    expect(processDiagramStore.state.diagramModel).not.toBe(null);
    expect(processDiagramStore.state.statistics).toEqual(
      firstProcessStatisticsResponse
    );

    await user.click(screen.getByText(/go to event based/i));

    await waitFor(() =>
      expect(processDiagramStore.state.status).toBe('fetching')
    );

    await waitFor(() =>
      expect(processDiagramStore.state.status).toBe('fetched')
    );
    expect(processDiagramStore.state.diagramModel).not.toBe(null);
    expect(processDiagramStore.state.statistics).toEqual(mockProcessStatistics);

    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    await user.click(screen.getByText(/go to no filters/i));

    await waitFor(() =>
      expect(processDiagramStore.state.statistics).toEqual([])
    );
  });

  it('should refetch data when navigated from header', async () => {
    const {user} = render(
      <>
        <AppHeader />
        <Processes />
      </>,
      {
        wrapper: getWrapper('/processes?active=true&incidents=true'),
      }
    );

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    await waitFor(() =>
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument()
    );

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).getByRole('link', {
        name: /processes/i,
      })
    );
    expect(await screen.findByTestId('diagram-spinner')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument()
    );

    expect(await screen.findByTestId('instances-loader')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument()
    );
  });
});
