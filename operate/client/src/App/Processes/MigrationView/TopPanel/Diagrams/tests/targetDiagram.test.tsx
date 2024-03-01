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

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessWithInputOutputMappingsXML,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {TargetDiagram} from '../TargetDiagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {Wrapper} from './mocks';

describe('Target Diagram', () => {
  it('should display initial state in the diagram header and diagram panel', async () => {
    processInstanceMigrationStore.setCurrentStep('elementMapping');

    render(<TargetDiagram />, {wrapper: Wrapper});

    expect(screen.getByText('Target')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', {
        name: /target process/i,
      }),
    ).toHaveTextContent(/Select target process/i);
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toHaveTextContent('-');

    expect(
      screen.getByText('Select a target process and version'),
    ).toBeInTheDocument();
  });

  it('should render process and version components according to the step number', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    await processesStore.fetchProcesses();
    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      screen.getByRole('combobox', {
        name: /target process/i,
      }),
    ).toHaveTextContent(/New demo process/i);
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toHaveTextContent('3');
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toBeEnabled();

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /summary/i}));

    expect(
      screen.queryByRole('combobox', {
        name: /target process/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: /target version/i,
      }),
    ).not.toBeInTheDocument();

    expect(screen.getByText('New demo process')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /element mapping/i}));

    expect(
      screen.getByRole('combobox', {
        name: /target process/i,
      }),
    ).toHaveTextContent(/New demo process/i);
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toHaveTextContent('3');
  });

  it('should render diagram on selection and re-render on version change', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    await processesStore.fetchProcesses();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /reset diagram zoom/i}));
    expect(screen.getByRole('button', {name: /zoom in diagram/i}));
    expect(screen.getByRole('button', {name: /zoom out diagram/i}));

    mockFetchProcessXML().withDelay(mockProcessWithInputOutputMappingsXML);

    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '2'}));

    expect(await screen.findByTestId('diagram-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('diagram-spinner'),
    );
  });

  it('should display error message on selection if diagram could not be fetched', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withServerError();
    await processesStore.fetchProcesses();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should render flow node overlays', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    await processesStore.fetchProcesses();
    await processStatisticsStore.fetchProcessStatistics();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));
    await user.click(screen.getByRole('button', {name: /map elements/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /summary/i}));

    expect(
      await screen.findByTestId('modifications-overlay'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('modifications-overlay')).toHaveTextContent('1');

    await user.click(screen.getByRole('button', {name: /element mapping/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(
      screen.queryByTestId('modifications-overlay'),
    ).not.toBeInTheDocument();
  });
});
