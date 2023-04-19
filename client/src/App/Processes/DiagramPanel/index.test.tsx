/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessInstances,
} from 'modules/testUtils';
import {DiagramPanel} from './index';
import {processesStore} from 'modules/stores/processes';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';
import {act} from 'react-dom/test-utils';

jest.mock('modules/utils/bpmn');

function getWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processDiagramStore.reset();
        processesStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <ThemeProvider>{children}</ThemeProvider>
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    processesStore.fetchProcesses();
  });

  it('should render header', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=1'),
    });

    expect(await screen.findByText('Big variable process')).toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=1'),
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
  });

  it('should show an empty state message when no process is selected', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByText('There is no Process selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel'
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show a message when no process version is selected', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=all'),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "Big variable process"'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a Diagram, select a single Version')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should display bpmnProcessId as process name in the message when no process version is selected', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper(
        '/processes?process=eventBasedGatewayProcess&version=all'
      ),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "eventBasedGatewayProcess"'
      )
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withNetworkError();

    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    act(() => {
      processDiagramStore.fetchProcessDiagram('1');
    });

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/)
    ).not.toBeInTheDocument();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    act(() => {
      processDiagramStore.fetchProcessDiagram('2');
    });

    expect(await screen.findByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));

    expect(
      screen.queryByText('Diagram could not be fetched')
    ).not.toBeInTheDocument();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withServerError();

    act(() => {
      processDiagramStore.fetchProcessDiagram('3');
    });

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/)
    ).not.toBeInTheDocument();
  });
});
