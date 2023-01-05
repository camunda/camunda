/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MetricPanel} from './index';
import {statistics} from 'modules/mocks/statistics';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessCoreStatistics} from 'modules/mocks/api/processInstances/fetchProcessCoreStatistics';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/processes" element={<div>Processes</div>} />
            <Route path="/" element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('<MetricPanel />', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

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
      'Running Process Instances in total'
    );

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instances-bar-skeleton')
    );
    expect(
      screen.getByText('1087 Running Process Instances in total')
    ).toBeInTheDocument();
  });

  it('should show active process instances and process instances with incidents', async () => {
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByText('Process Instances with Incident')
    ).toBeInTheDocument();
    expect(screen.getByText('Active Process Instances')).toBeInTheDocument();
    expect(
      await screen.findByTestId('incident-instances-badge')
    ).toHaveTextContent('877');
    expect(
      await screen.findByTestId('active-instances-badge')
    ).toHaveTextContent('210');
  });

  it('should go to the correct page when clicking on instances with incidents', async () => {
    const {user} = render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instances-bar-skeleton')
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);
    await user.click(screen.getByText('Process Instances with Incident'));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?incidents=true$/
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should go to the correct page when clicking on active process instances', async () => {
    const {user} = render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instances-bar-skeleton')
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
      await screen.findByText('1087 Running Process Instances in total')
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?incidents=true&active=true$/
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should handle server errors', async () => {
    mockFetchProcessCoreStatistics().withServerError();

    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle networks errors', async () => {
    mockFetchProcessCoreStatistics().withNetworkError();

    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched')
    ).toBeInTheDocument();
  });
});
