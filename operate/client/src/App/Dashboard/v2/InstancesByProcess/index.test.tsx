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
  within,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {InstancesByProcess} from './index';
import {mockWithSingleVersion, mockWithMultipleVersions} from './index.setup';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessInstancesByName} from 'modules/mocks/api/incidents/fetchProcessInstancesByName';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {createUser} from 'modules/testUtils';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {mockMe} from 'modules/mocks/api/v2/me';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        panelStatesStore.reset();
        processInstancesByNameStore.reset();
      };
    }, []);

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

describe('InstancesByProcess', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
    mockMe().withSuccess(createUser());
  });

  it('should display skeleton when loading', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(
      await screen.findByTestId('instances-by-process'),
    ).toBeInTheDocument();

    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
  });

  it('should handle server errors', async () => {
    mockFetchProcessInstancesByName().withServerError();
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should handle network errors', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockFetchProcessInstancesByName().withNetworkError();
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should display information message when there are no processes', async () => {
    mockFetchProcessInstancesByName().withSuccess([]);
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Start by deploying a process'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'There are no processes deployed. Deploy and start a process from our Modeler, then come back here to track its progress.',
      ),
    ).toBeInTheDocument();
  });

  it('should render items with more than one processes versions', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockWithMultipleVersions);
    processInstancesByNameStore.getProcessInstancesByName();

    const {user} = render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('instances-by-process-0'),
    );

    const processLink = withinIncident.getByRole('link', {
      description: /View 201 Instances in 2 Versions of Process Order process/,
    });

    expect(processLink).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=orderProcess&version=all&active=true&incidents=true`,
    );

    expect(
      within(processLink).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('65');
    expect(
      within(processLink).getByTestId('active-instances-badge'),
    ).toHaveTextContent('136');

    const expandButton = withinIncident.getByRole('button', {
      name: 'Expand current row',
    });

    // this button click has no effect (check useEffect in Collapse component)
    await user.click(expandButton);

    const firstVersion = await screen.findByRole('link', {
      description: /View 42 Instances in Version 1 of Process First Version/,
    });

    expect(
      within(firstVersion).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByTestId('active-instances-badge'),
    ).toHaveTextContent('5');
    expect(
      within(firstVersion).getByText(
        'First Version – 42 Instances in Version 1',
      ),
    ).toBeInTheDocument();
    expect(firstVersion).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=mockProcess&version=1&active=true&incidents=true`,
    );

    const secondVersion = screen.getByRole('link', {
      description: 'View 42 Instances in Version 2 of Process Second Version',
    });

    expect(
      within(secondVersion).getByTestId('incident-instances-badge'),
    ).toHaveTextContent('37');
    expect(
      within(secondVersion).getByTestId('active-instances-badge'),
    ).toHaveTextContent('5');
    expect(
      within(secondVersion).getByText(
        'Second Version – 42 Instances in Version 2',
      ),
    ).toBeInTheDocument();
    expect(secondVersion).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=mockProcess&version=2&active=true&incidents=true`,
    );
  });

  it('should render items with one process version', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });
    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    const withinIncident = within(
      await screen.findByTestId('instances-by-process-0'),
    );

    expect(
      withinIncident.queryByTestId('expand-button'),
    ).not.toBeInTheDocument();

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version'),
    ).toBeInTheDocument();

    const processLink = screen.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    expect(processLink).toHaveAttribute(
      'href',
      `${Paths.processes()}?process=loanProcess&version=1&active=true&incidents=true`,
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '16',
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '122',
    );
  });

  it('should expand filters panel on click', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.getProcessInstancesByName();

    const {user} = render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );

    const processLink = screen.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    await user.click(processLink);

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?process=loanProcess&version=1&active=true&incidents=true$/,
      ),
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should update after next poll', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.init();
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('instances-by-process-0'),
    );

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version'),
    ).toBeInTheDocument();

    mockFetchProcessInstancesByName().withSuccess([
      {...mockWithSingleVersion[0]!, activeInstancesCount: 142},
    ]);

    vi.runOnlyPendingTimers();

    expect(
      await withinIncident.findByText(
        'loanProcess – 158 Instances in 1 Version',
      ),
    ).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render modeler button', async () => {
    mockFetchProcessInstancesByName().withSuccess([]);
    mockMe().withSuccess(
      createUser({
        c8Links: [{name: 'modeler', link: 'https://link-to-modeler'}],
      }),
    );

    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByRole('button', {name: 'Go to Modeler'}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: 'Go to Modeler'}).closest('a'),
    ).toHaveAttribute('href', 'https://link-to-modeler');
  });

  it('should not render modeler button', async () => {
    mockFetchProcessInstancesByName().withSuccess([]);

    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByRole('button', {name: 'Go to Modeler'}),
    ).not.toBeInTheDocument();
  });
});
