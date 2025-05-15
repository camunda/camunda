/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessInstances,
} from 'modules/testUtils';
import {DiagramPanel} from './index';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.list';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';
import {act} from 'react-dom/test-utils';
import {Paths} from 'modules/Routes';
import {batchModificationStore} from 'modules/stores/batchModification';

jest.mock('modules/utils/bpmn');

function getWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processXmlStore.reset();
        processStatisticsStore.reset();
        processesStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>
        {children}
        <button onClick={batchModificationStore.enable}>
          Enable batch modification mode
        </button>
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  const originalWindow = {...window};
  const locationSpy = jest.spyOn(window, 'location', 'get');

  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    processesStore.fetchProcesses();
  });

  afterEach(() => {
    locationSpy.mockClear();
  });

  it('should render header', async () => {
    const originalWindowPrompt = window.prompt;
    window.prompt = jest.fn();

    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    const {user} = render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByText('Big variable process')).toBeInTheDocument();
    expect(screen.getByText('bigVarProcess')).toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {
        name: 'Process ID / Click to copy',
      }),
    );
    expect(await screen.findByText('Copied to clipboard')).toBeInTheDocument();

    window.prompt = originalWindowPrompt;
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
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
      await screen.findByText('There is no Process selected'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show a message when no process version is selected', async () => {
    const queryString = '?process=bigVarProcess&version=all';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "Big variable process"',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a Diagram, select a single Version'),
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should display bpmnProcessId as process name in the message when no process version is selected', async () => {
    const queryString = '?process=eventBasedGatewayProcess&version=all';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "eventBasedGatewayProcess"',
      ),
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withServerError();

    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    await act(async () => {
      await processXmlStore.fetchProcessXml('1');
      await processStatisticsStore.fetchProcessStatistics();
    });

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/),
    ).not.toBeInTheDocument();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    await act(async () => {
      await processXmlStore.fetchProcessXml('2');
      await processStatisticsStore.fetchProcessStatistics();
    });

    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withServerError();

    await act(async () => {
      await processXmlStore.fetchProcessXml('3');
      await processStatisticsStore.fetchProcessStatistics();
    });

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/),
    ).not.toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should render diagram when statistics endpoint fails', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    const handleFetchErrorSpy = jest.spyOn(
      processStatisticsStore,
      'handleFetchError',
    );

    mockFetchProcessInstancesStatistics().withServerError();
    mockFetchProcessXML().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    await waitFor(() => expect(handleFetchErrorSpy).toHaveBeenCalled());
    expect(screen.queryByTestId('state-overlay')).not.toBeInTheDocument();
  });

  it('should render statistics', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(await screen.findByTestId('state-overlay')).toBeInTheDocument();
  });

  it.skip('should clear statistics before fetching new statistics', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(await screen.findByTestId('state-overlay')).toBeInTheDocument();

    mockFetchProcessInstancesStatistics().withServerError();

    await act(async () => {
      await processStatisticsStore.fetchProcessStatistics();
    });

    expect(screen.queryByTestId('state-overlay')).not.toBeInTheDocument();
  });

  it('should render batch modification notification', async () => {
    const {user} = render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    const notificationText =
      'Please select where you want to move the selected instances on the diagram.';

    expect(screen.queryByText(notificationText)).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    expect(await screen.findByText(notificationText)).toBeInTheDocument();
  });
});
