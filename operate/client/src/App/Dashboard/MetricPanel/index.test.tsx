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

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
} from 'modules/testing-library';
import {MetricPanel} from './index';
import {statistics} from 'modules/mocks/statistics';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessCoreStatistics} from 'modules/mocks/api/processInstances/fetchProcessCoreStatistics';
import {Paths} from 'modules/Routes';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.processes()} element={<div>Processes</div>} />
          <Route path={Paths.dashboard()} element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('<MetricPanel />', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
    mockFetchProcessCoreStatistics().withSuccess(statistics);
  });

  afterEach(() => {
    panelStatesStore.reset();
  });

  it('should first display skeleton, then the statistics', async () => {
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('instances-bar-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('total-instances-link')).toHaveTextContent(
      'Running Process Instances in total',
    );

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instances-bar-skeleton'),
    );
    expect(
      screen.getByText('1087 Running Process Instances in total'),
    ).toBeInTheDocument();
  });

  it('should show active process instances and process instances with incidents', async () => {
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByText('Process Instances with Incident'),
    ).toBeInTheDocument();
    expect(screen.getByText('Active Process Instances')).toBeInTheDocument();
    expect(
      await screen.findByTestId('incident-instances-badge'),
    ).toHaveTextContent('877');
    expect(
      await screen.findByTestId('active-instances-badge'),
    ).toHaveTextContent('210');
  });

  it('should go to the correct page when clicking on instances with incidents', async () => {
    const {user} = render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instances-bar-skeleton'),
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);
    await user.click(screen.getByText('Process Instances with Incident'));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?incidents=true$/,
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should go to the correct page when clicking on active process instances', async () => {
    const {user} = render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instances-bar-skeleton'),
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(screen.getByText('Active Process Instances'));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?active=true$/);

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should go to the correct page when clicking on total instances', async () => {
    const {user} = render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(
      await screen.findByText('1087 Running Process Instances in total'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?incidents=true&active=true$/,
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should handle server errors', async () => {
    mockFetchProcessCoreStatistics().withServerError();

    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should handle networks errors', async () => {
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchProcessCoreStatistics().withNetworkError();

    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched'),
    ).toBeInTheDocument();
    consoleErrorMock.mockRestore();
  });
});
