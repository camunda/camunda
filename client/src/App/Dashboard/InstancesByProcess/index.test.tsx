/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, within, screen, waitFor} from 'modules/testing-library';
import {InstancesByProcess} from './index';
import {mockWithSingleVersion, mockWithMultipleVersions} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessInstancesByName} from 'modules/mocks/api/incidents/fetchProcessInstancesByName';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {createUser} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        panelStatesStore.reset();
        processInstancesByNameStore.reset();
      };
    }, []);

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

describe('InstancesByProcess', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
  });

  it('should display skeleton when loading', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    expect(
      await screen.findByTestId('instances-by-process')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
  });

  it('should handle server errors', async () => {
    mockFetchProcessInstancesByName().withServerError();
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle network errors', async () => {
    mockFetchProcessInstancesByName().withNetworkError();
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display information message when there are no processes', async () => {
    mockFetchProcessInstancesByName().withSuccess([]);
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Start by deploying a process')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'There are no processes deployed. Deploy and start a process from our Modeler, then come back here to track its progress.'
      )
    ).toBeInTheDocument();
  });

  it.skip('should render items with more than one processes versions', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockWithMultipleVersions);
    processInstancesByNameStore.getProcessInstancesByName();

    const {user} = render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    const processLink = withinIncident.getByRole('link', {
      description: /View 201 Instances in 2 Versions of Process Order process/,
    });

    expect(processLink).toHaveAttribute(
      'href',
      '/processes?process=orderProcess&version=all&active=true&incidents=true'
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '65'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '136'
    );

    const expandButton = withinIncident.getByTitle(
      'Expand 201 Instances of Process Order process'
    );

    // this button click has no effect (check useEffect in Collapse component)
    await user.click(expandButton);

    const firstVersion = await screen.findByRole('link', {
      description: /View 42 Instances in Version 1 of Process First Version/,
    });

    expect(
      within(firstVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(firstVersion).getByText(
        'First Version – 42 Instances in Version 1'
      )
    ).toBeInTheDocument();
    expect(firstVersion).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=1&active=true&incidents=true'
    );

    const secondVersion = screen.getByRole('link', {
      description: 'View 42 Instances in Version 2 of Process Second Version',
    });

    expect(
      within(secondVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(secondVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(secondVersion).getByText(
        'Second Version – 42 Instances in Version 2'
      )
    ).toBeInTheDocument();
    expect(secondVersion).toHaveAttribute(
      'href',
      '/processes?process=mockProcess&version=2&active=true&incidents=true'
    );
  });

  it('should render items with one process version', async () => {
    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    expect(
      withinIncident.queryByTestId('expand-button')
    ).not.toBeInTheDocument();

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
    ).toBeInTheDocument();

    const processLink = withinIncident.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    expect(processLink).toHaveAttribute(
      'href',
      '/processes?process=loanProcess&version=1&active=true&incidents=true'
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '16'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '122'
    );
  });

  it('should expand filters panel on click', async () => {
    jest.useFakeTimers();
    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.getProcessInstancesByName();

    const {user} = render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    const processLink = withinIncident.getByRole('link', {
      description: 'View 138 Instances in 1 Version of Process loanProcess',
    });

    await user.click(processLink);

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?process=loanProcess&version=1&active=true&incidents=true$/
      )
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should update after next poll', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.init();
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
    ).toBeInTheDocument();

    mockFetchProcessInstancesByName().withSuccess([
      {...mockWithSingleVersion[0]!, activeInstancesCount: 142},
    ]);

    jest.runOnlyPendingTimers();

    expect(
      await withinIncident.findByText(
        'loanProcess – 158 Instances in 1 Version'
      )
    ).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render modeler button', async () => {
    mockFetchProcessInstancesByName().withSuccess([]);

    authenticationStore.setUser(
      createUser({
        c8Links: {
          modeler: 'https://link-to-modeler',
        },
      })
    );
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByRole('button', {name: 'Go to Modeler'})
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: 'Go to Modeler'}).closest('a')
    ).toHaveAttribute('href', 'https://link-to-modeler');
  });

  it('should not render modeler button', async () => {
    mockFetchProcessInstancesByName().withSuccess([]);

    authenticationStore.setUser(createUser());
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByRole('button', {name: 'Go to Modeler'})
    ).not.toBeInTheDocument();
  });
});
