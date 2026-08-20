/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
} from 'modules/testing-library';
import {MetricPanel} from './index';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionStatistics} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionStatistics';
import {searchResult} from 'modules/testUtils';
import {createProcessDefinitionInstancesStatistics} from 'modules/mocks/mockProcessDefinitionStatistics';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path={Paths.processes()} element={<div>Processes</div>} />
            <Route path={Paths.dashboard()} element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('<MetricPanel />', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
    mockFetchProcessDefinitionStatistics().withSuccess(
      searchResult([
        createProcessDefinitionInstancesStatistics({
          activeInstancesWithIncidentCount: 877,
          activeInstancesWithoutIncidentCount: 210,
        }),
      ]),
    );
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
      screen.queryByTestId('instances-bar-skeleton'),
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

  it('should show aggregated instances counts from all process definition instances statistics', async () => {
    mockFetchProcessDefinitionStatistics().withSuccess(
      searchResult([
        createProcessDefinitionInstancesStatistics({
          activeInstancesWithIncidentCount: 6,
          activeInstancesWithoutIncidentCount: 2,
        }),
        createProcessDefinitionInstancesStatistics({
          activeInstancesWithIncidentCount: 6,
          activeInstancesWithoutIncidentCount: 2,
        }),
        createProcessDefinitionInstancesStatistics({
          activeInstancesWithIncidentCount: 6,
          activeInstancesWithoutIncidentCount: 2,
        }),
      ]),
    );
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('24 Running Process Instances in total'),
    ).toBeInTheDocument();
    expect(
      await screen.findByTestId('incident-instances-badge'),
    ).toHaveTextContent('18');
    expect(
      await screen.findByTestId('active-instances-badge'),
    ).toHaveTextContent('6');
  });

  it('should go to the correct page when clicking on instances with incidents', async () => {
    const {user} = render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('instances-bar-skeleton'),
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
      screen.queryByTestId('instances-bar-skeleton'),
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
    mockFetchProcessDefinitionStatistics().withServerError();

    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should handle networks errors', async () => {
    mockFetchProcessDefinitionStatistics().withNetworkError();

    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched'),
    ).toBeInTheDocument();
  });
});
