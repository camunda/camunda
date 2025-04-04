/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {processesStore} from 'modules/stores/processes/processes.list';
import {
  createUser,
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {
  selectProcess,
  selectTenant,
} from 'modules/testUtils/selectComboBoxOption';
import {mockMe} from 'modules/mocks/api/v2/me';
import {authenticationStore} from 'modules/stores/authentication';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

jest.unmock('modules/utils/date/formatDate');

describe('Filters', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: '<tenant-A>', name: 'Tenant A'},
          {tenantId: 'tenant-b', name: 'Tenant B'},
        ],
      }),
    );
    processesStore.fetchProcesses();
    await authenticationStore.authenticate();
  });

  it('should load values from the URL when multi tenancy is enabled', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    const MOCK_PARAMS = {
      process: 'bigVarProcess',
      version: '1',
      tenant: '<tenant-A>',
    } as const;

    render(<Filters />, {
      wrapper: getWrapper(
        `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`,
      ),
    });

    await waitFor(() =>
      expect(
        screen.getByRole('combobox', {
          name: 'Name',
        }),
      ).toBeEnabled(),
    );
    expect(
      screen.getByRole('combobox', {
        name: 'Name',
      }),
    ).toHaveValue('Big variable process');
    expect(
      screen.getByRole('combobox', {
        name: /tenant/i,
      }),
    ).toHaveTextContent(/tenant a/i);
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent('1');
  });

  it('should hide multi tenancy filter if its not enabled in client config', async () => {
    const MOCK_PARAMS = {
      process: 'bigVarProcess',
      version: '1',
      ids: '2251799813685467',
      parentInstanceId: '1954699813693756',
      errorMessage: 'a random error',
      flowNodeId: 'ServiceTask_0kt6c5i',
      variableName: 'foo',
      variableValue: 'bar',
      operationId: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
      active: 'true',
      incidents: 'true',
      completed: 'true',
      canceled: 'true',
      tenant: '<tenant-A>',
    } as const;

    render(<Filters />, {
      wrapper: getWrapper(
        `/?${new URLSearchParams(Object.entries(MOCK_PARAMS)).toString()}`,
      ),
    });

    expect(
      screen.queryByRole('combobox', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should set modified values to the URL when multi tenancy is enabled', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    const MOCK_VALUES = {
      process: 'bigVarProcess',
      version: '2',
      tenant: '<tenant-A>',
    } as const;
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await selectTenant({user, option: 'All tenants'});
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /all tenants/i,
    );

    await waitFor(() =>
      expect(
        Object.fromEntries(
          new URLSearchParams(
            screen.getByTestId('search').textContent ?? '',
          ).entries(),
        ),
      ).toEqual(
        expect.objectContaining({
          tenant: 'all',
        }),
      ),
    );

    await waitFor(() => expect(screen.getByLabelText('Name')).toBeEnabled());

    await selectProcess({
      user,
      option: 'Big variable process - Tenant A',
    });

    expect(screen.getByLabelText('Name')).toHaveValue('Big variable process');
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /tenant a/i,
    );

    await waitFor(() =>
      expect(
        Object.fromEntries(
          new URLSearchParams(
            screen.getByTestId('search').textContent ?? '',
          ).entries(),
        ),
      ).toEqual(expect.objectContaining(MOCK_VALUES)),
    );

    window.clientConfig = undefined;
  });

  it('should disable processes field when tenant is not selected', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await waitFor(() => expect(processesStore.state.status).toBe('fetched'));
    expect(screen.getByLabelText('Name')).toBeDisabled();

    window.clientConfig = undefined;
  });

  it('should clear process and version field when tenant filter is changed', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await waitFor(() => expect(processesStore.state.status).toBe('fetched'));
    expect(screen.getByLabelText('Name')).toBeDisabled();

    await selectTenant({user, option: 'All tenants'});
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /all tenants/i,
    );

    await waitFor(() => expect(screen.getByLabelText('Name')).toBeEnabled());

    await selectProcess({
      user,
      option: 'Big variable process - Default Tenant',
    });

    expect(screen.getByLabelText('Name')).toHaveValue('Big variable process');
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent('1');
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /default tenant/i,
    );

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    await selectTenant({user, option: 'Tenant B'});
    await waitFor(() => expect(processesStore.state.status).toBe('fetched'));

    expect(screen.getByLabelText('Name')).toHaveValue('');
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent(/select a process version/i);

    window.clientConfig = undefined;
  });
});
