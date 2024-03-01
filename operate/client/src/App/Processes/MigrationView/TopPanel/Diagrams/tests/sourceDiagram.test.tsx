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

import {render, screen} from 'modules/testing-library';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {SourceDiagram} from '../SourceDiagram';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.source';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {Wrapper} from './mocks';

describe('Source Diagram', () => {
  it('should render process name and version', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    const originalWindow = {...window};
    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: '?active=true&incidents=true&process=demoProcess&version=3',
    }));

    processesStore.fetchProcesses();
    render(<SourceDiagram />, {wrapper: Wrapper});

    expect(await screen.findByText('New demo process')).toBeInTheDocument();
    expect(screen.getByText('Source')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();

    locationSpy.mockRestore();
  });

  it('should render xml', async () => {
    processXmlStore.setProcessXml(mockProcessXML);

    render(<SourceDiagram />, {wrapper: Wrapper});

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /reset diagram zoom/i}));
    expect(screen.getByRole('button', {name: /zoom in diagram/i}));
    expect(screen.getByRole('button', {name: /zoom out diagram/i}));
  });

  it('should render statistics overlays', async () => {
    processXmlStore.setProcessXml(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    processStatisticsStore.fetchProcessStatistics();

    const {user} = render(<SourceDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /summary/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(await screen.findByTestId('state-overlay')).toBeInTheDocument();
    expect(screen.getByTestId('state-overlay')).toHaveTextContent('1');

    await user.click(screen.getByRole('button', {name: /element mapping/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(screen.queryByTestId('state-overlay')).not.toBeInTheDocument();
  });
});
