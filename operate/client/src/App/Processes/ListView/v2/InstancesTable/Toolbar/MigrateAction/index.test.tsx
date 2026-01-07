/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act} from '@testing-library/react';
import {render, screen, waitFor} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {MigrateAction} from '.';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {tracking} from 'modules/tracking';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter} from 'react-router-dom';
import {
  getProcessInstance,
  setupSelectionStoreWithInstances,
  mockProcessInstancesV2,
} from '../../../tests/testUtils';
import {useEffect} from 'react';
import {batchModificationStore} from 'modules/stores/batchModification';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

const PROCESS_DEFINITION_ID = '2251799813685249';
const PROCESS_ID = 'eventBasedGatewayProcess';

const mockCalledProcessInstancesV2: ProcessInstance[] = [
  {
    processInstanceKey: '5',
    processDefinitionId: PROCESS_DEFINITION_ID,
    processDefinitionKey: PROCESS_DEFINITION_ID,
    processDefinitionName: 'Event Based Gateway Process',
    processDefinitionVersion: 1,
    state: 'ACTIVE',
    startDate: '2023-01-01T00:00:00.000+0000',
    hasIncident: false,
    tenantId: '<default>',
    parentProcessInstanceKey: '999',
  },
];

vi.mock('modules/stores/processes/processes.list', () => {
  const PROCESS_DEFINITION_ID = '2251799813685249';
  const PROCESS_ID = 'eventBasedGatewayProcess';
  return {
    processesStore: {
      getPermissions: vi.fn(),
      getProcessId: () => PROCESS_ID,
      state: {processes: []},
      versionsByProcessAndTenant: {
        [`{${PROCESS_ID}}-{<default>}`]: [
          {id: PROCESS_DEFINITION_ID, version: 1},
        ],
      },
    },
  };
});

function getWrapper(initialPath: string = '') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      processInstancesSelectionStore.init();
      return () => {
        processInstancesSelectionStore.reset();
        processInstanceMigrationStore.reset();
        batchModificationStore.reset();
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
        <button onClick={batchModificationStore.enable}>
          Enter batch modification mode
        </button>
        <button onClick={batchModificationStore.reset}>
          Exit batch modification mode
        </button>
      </MemoryRouter>
    );
  };

  return Wrapper;
}

function getWrapperWithQueryClient(initialPath: string = '') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    const InnerWrapper = getWrapper(initialPath);
    return (
      <ProcessDefinitionKeyContext.Provider value={PROCESS_DEFINITION_ID}>
        <QueryClientProvider client={getMockQueryClient()}>
          <InnerWrapper>{children}</InnerWrapper>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  return Wrapper;
}

describe('<MigrateAction />', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should disable migrate button, when no process version is selected', () => {
    render(<MigrateAction />, {wrapper: getWrapperWithQueryClient()});

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should disable migrate button, when no active or incident instances are selected', () => {
    render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should enable migrate button, when active or incident instances are selected', () => {
    render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);
    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should enable migrate button when selected instances are called by parent', () => {
    render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockCalledProcessInstancesV2);
    const instance = getProcessInstance('ACTIVE', mockCalledProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should disable migrate button, when process XML could not be loaded', async () => {
    mockFetchProcessDefinitionXml().withServerError();

    render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);
    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
    });
  });

  it('should disable migrate button, when only finished instances are selected', () => {
    render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);
    const instance = getProcessInstance('TERMINATED', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should enable migrate button, when all instances are selected', async () => {
    const {user} = render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should display migration helper modal on button click', async () => {
    const {user} = render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);
    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    await user.click(screen.getByRole('button', {name: /migrate/i}));

    expect(
      screen.getByText(
        'Migrate is used to migrate running process instances to a different process definition.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'When the migration steps are executed, all selected process instances will be affected. This can lead to interruptions, delays or changes.',
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

  it.skip('should set correct store states after migrate click', async () => {
    const SEARCH_STRING = `?process=${PROCESS_ID}&version=1&active=true&incidents=false`;
    vi.stubGlobal('clientConfig', {
      ...window.clientConfig,
      search: SEARCH_STRING,
    });

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(`/processes${SEARCH_STRING}`),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);
    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });
    await user.click(screen.getByRole('button', {name: /migrate/i}));

    await user.click(screen.getByRole('button', {name: /continue/i}));

    expect(processInstanceMigrationStore.state.currentStep).toBe(
      'elementMapping',
    );
    expect(processInstanceMigrationStore.state.batchOperationQuery).toEqual({
      active: true,
      excludeIds: [],
      ids: [instance.processInstanceKey],
      incidents: false,
      processIds: [PROCESS_DEFINITION_ID],
      running: true,
    });
  });

  it('should track migrate click', async () => {
    const trackSpy = vi.spyOn(tracking, 'track');

    const {user} = render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);
    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        String(instance.processInstanceKey),
      );
    });
    await user.click(screen.getByRole('button', {name: /migrate/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'process-instance-migration-button-clicked',
    });

    await user.click(screen.getByRole('button', {name: /continue/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'process-instance-migration-mode-entered',
    });

    trackSpy.mockRestore();
  });

  it('should disable migrate action in batch modification mode', async () => {
    const {user} = render(<MigrateAction />, {
      wrapper: getWrapperWithQueryClient(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

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
