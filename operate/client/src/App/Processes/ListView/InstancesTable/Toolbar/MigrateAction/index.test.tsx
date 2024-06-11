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

import {act} from '@testing-library/react';
import {render, screen, waitFor} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {
  mockCalledProcessInstances,
  mockProcessInstances,
  mockProcessStatistics,
} from 'modules/testUtils';
import {MigrateAction} from '.';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {tracking} from 'modules/tracking';
import {fetchProcessInstances, getProcessInstance, getWrapper} from '../mocks';

const PROCESS_DEFINITION_ID = '2251799813685249';
const PROCESS_ID = 'eventBasedGatewayProcess';

jest.mock('modules/stores/processes/processes.list', () => ({
  processesStore: {
    getPermissions: jest.fn(),
    state: {processes: []},
    versionsByProcessAndTenant: {
      [`{${PROCESS_ID}}-{<default>}`]: [
        {id: PROCESS_DEFINITION_ID, version: 1},
      ],
    },
  },
}));

describe('<MigrateAction />', () => {
  it('should disable migrate button, when no process version is selected', () => {
    render(<MigrateAction />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should disable migrate button, when no active or incident instances are selected', () => {
    render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should enable migrate button, when active or incident instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should enable migrate button, when selected instances are called by parent', async () => {
    mockFetchProcessInstances().withSuccess(mockCalledProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockCalledProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should disable migrate button, when process XML could not be loaded', async () => {
    mockFetchProcessInstances().withSuccess(mockCalledProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    mockFetchProcessXML().withServerError();
    await waitFor(() => processXmlStore.fetchProcessXml('1'));
    expect(processXmlStore.state.status).toBe('error');

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should disable migrate button, when only finished instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('CANCELED', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should enable migrate button, when all instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();

    await fetchProcessInstances(screen, user);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should display migration helper modal on button click', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    await user.click(screen.getByRole('button', {name: /migrate/i}));

    expect(
      screen.getByText(
        'Migrate is used to move a process to a newer (or older) version of the process.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'When the migration steps are executed, all process instances are affected. This can lead to interruptions, delays, or changes.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To minimize interruptions or delays, plan the migration at times when the system load is low.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'migration documentation'}),
    ).toBeInTheDocument();
  });

  it('should set correct store states after migrate click', async () => {
    const SEARCH_STRING = `?process=${PROCESS_ID}&version=1&active=true&incidents=false`;

    const originalWindow = {...window};
    const locationSpy = jest.spyOn(window, 'location', 'get');
    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: SEARCH_STRING,
    }));

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(`/processes${SEARCH_STRING}`),
    });

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);
    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });
    await user.click(screen.getByRole('button', {name: /migrate/i}));

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    await user.click(screen.getByRole('button', {name: /continue/i}));

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        mockProcessStatistics,
      ),
    );
    expect(processInstanceMigrationStore.state.currentStep).toBe(
      'elementMapping',
    );
    expect(processInstanceMigrationStore.state.batchOperationQuery).toEqual({
      processIds: [PROCESS_DEFINITION_ID],
      active: true,
      running: true,
      incidents: false,
      ids: [instance.id],
      excludeIds: [],
    });

    locationSpy.mockRestore();
  });

  it('should track migrate click', async () => {
    const trackSpy = jest.spyOn(tracking, 'track');

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);
    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });
    await user.click(screen.getByRole('button', {name: /migrate/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'process-instance-migration-button-clicked',
    });

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    await user.click(screen.getByRole('button', {name: /continue/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'process-instance-migration-mode-entered',
    });

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        mockProcessStatistics,
      ),
    );

    trackSpy.mockRestore();
  });

  it('should disable migrate action in batch modification mode', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances(screen, user);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();

    await user.click(
      screen.getByRole('button', {name: /enter batch modification mode/i}),
    );

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });
});
