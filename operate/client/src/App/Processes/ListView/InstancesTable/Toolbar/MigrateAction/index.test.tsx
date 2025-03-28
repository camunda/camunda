/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    getProcessId: jest.fn(),
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

  it('should enable migrate button when selected instances are called by parent', async () => {
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
