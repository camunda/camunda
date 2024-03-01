/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        panelStatesStore.reset();
        processInstancesByNameStore.reset();
      };
    }, []);

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
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

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

  it.skip('should render items with more than one processes versions', async () => {
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
      screen.getByTestId('data-table-skeleton'),
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
    jest.useFakeTimers();
    mockFetchProcessInstancesByName().withSuccess(mockWithSingleVersion);
    processInstancesByNameStore.getProcessInstancesByName();

    const {user} = render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('data-table-skeleton'),
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
      await screen.findByTestId('instances-by-process-0'),
    );

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version'),
    ).toBeInTheDocument();

    mockFetchProcessInstancesByName().withSuccess([
      {...mockWithSingleVersion[0]!, activeInstancesCount: 142},
    ]);

    jest.runOnlyPendingTimers();

    expect(
      await withinIncident.findByText(
        'loanProcess – 158 Instances in 1 Version',
      ),
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

    authenticationStore.setUser(createUser());
    processInstancesByNameStore.getProcessInstancesByName();

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByRole('button', {name: 'Go to Modeler'}),
    ).not.toBeInTheDocument();
  });
});
