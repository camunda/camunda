/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {MemoryRouter} from 'react-router-dom';
import {act} from '@testing-library/react';
import {UserEvent, render, screen, waitFor} from 'modules/testing-library';
import {Paths} from 'modules/Routes';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {
  mockCalledProcessInstances,
  mockProcessInstances,
  mockProcessStatistics,
} from 'modules/testUtils';
import {MigrateAction} from '.';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {ProcessInstancesDto} from 'modules/api/processInstances/fetchProcessInstances';
import {tracking} from 'modules/tracking';

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

const fetchProcessInstances = async (user: UserEvent) => {
  await user.click(
    screen.getByRole('button', {name: /fetch process instances/i}),
  );
  await waitFor(() =>
    expect(processInstancesStore.state.status).toBe('fetched'),
  );
};

const getProcessInstance = (
  state: ProcessInstanceEntity['state'],
  mockData: ProcessInstancesDto,
) => {
  const instance = mockData.processInstances.find(
    (instance) => instance.state === state,
  );

  if (instance === undefined) {
    throw new Error(`please make sure there is a ${state} in mockData`);
  }

  return instance;
};

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
    ({children}) => {
      useEffect(() => {
        return () => {
          processInstancesSelectionStore.reset();
          processInstancesStore.reset();
          processInstanceMigrationStore.reset();
          processStatisticsStore.reset();
        };
      }, []);
      return (
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <button
            onClick={processInstancesSelectionStore.selectAllProcessInstances}
          >
            Select all instances
          </button>
          <button
            onClick={() =>
              processInstancesStore.fetchInstances({
                fetchType: 'initial',
                payload: {query: {}},
              })
            }
          >
            Fetch process instances
          </button>
        </MemoryRouter>
      );
    },
  );

  return Wrapper;
}

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

    await fetchProcessInstances(user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should disable migrate button, when selected instances are called by parent', async () => {
    mockFetchProcessInstances().withSuccess(mockCalledProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances(user);

    const instance = getProcessInstance('ACTIVE', mockCalledProcessInstances);

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

    await fetchProcessInstances(user);

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

    await fetchProcessInstances(user);

    act(() => {
      processInstancesSelectionStore.selectAllProcessInstances();
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should display migration helper modal on button click', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances(user);

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
        'When Migrate steps are run, all process instances will be affected. Interruptions, delays or changes may happen as a result.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To minimize interruptions or delays, schedule Migrate during periods of low system usage.',
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
    await fetchProcessInstances(user);

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
    await fetchProcessInstances(user);

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
});
