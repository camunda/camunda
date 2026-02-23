/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {
  createProcessDefinition,
  createUser,
  mockProcessXML,
  searchResult,
} from 'modules/testUtils';
import {Filters} from '../index';
import {
  selectProcess,
  selectTenant,
} from 'modules/testUtils/selectComboBoxOption';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import * as clientConfig from 'modules/utils/getClientConfig';

describe('Filters', () => {
  beforeEach(() => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: '<tenant-A>', name: 'Tenant A'},
          {key: 3, tenantId: 'tenant-b', name: 'Tenant B'},
        ],
      }),
    );
  });

  it('should load values from the URL when multi tenancy is enabled', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2, tenantId: '<tenant-A>'}),
        createProcessDefinition({version: 1, tenantId: '<tenant-A>'}),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 1, tenantId: '<tenant-A>'}),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2, tenantId: '<tenant-A>'}),
        createProcessDefinition({version: 1, tenantId: '<tenant-A>'}),
      ]),
    );

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
        name: /select a tenant/i,
      }),
    ).toHaveTextContent(/tenant a/i);
    expect(
      screen.getByRole('combobox', {name: 'Select a Process Version'}),
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
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2, tenantId: '<tenant-A>'}),
        createProcessDefinition({version: 1, tenantId: '<default>'}),
      ]),
    );
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const MOCK_VALUES = {
      process: 'bigVarProcess',
      version: '2',
      tenant: '<tenant-A>',
    } as const;
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2, tenantId: '<tenant-A>'}),
        createProcessDefinition({version: 1, tenantId: '<default>'}),
      ]),
    );
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

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toBeEnabled(),
    );

    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2, tenantId: '<tenant-A>'}),
        createProcessDefinition({version: 1, tenantId: '<tenant-A>'}),
      ]),
    );
    await selectProcess({
      user,
      option: 'Big variable process - Tenant A',
    });

    expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
      'Big variable process - Tenant A',
    );
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
  });

  it('should disable processes field when tenant is not selected', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();
  });

  it('should clear process and version field when tenant filter is changed', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2, tenantId: 'tenant-b'}),
        createProcessDefinition({version: 2, tenantId: '<tenant-A>'}),
        createProcessDefinition({version: 1, tenantId: '<default>'}),
      ]),
    );

    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();

    await selectTenant({user, option: 'All tenants'});
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /all tenants/i,
    );

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toBeEnabled(),
    );

    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2, tenantId: '<default>'}),
        createProcessDefinition({version: 1, tenantId: '<default>'}),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 1, tenantId: '<default>'}),
      ]),
    );
    await selectProcess({
      user,
      option: 'Big variable process - Default Tenant',
    });

    expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
      'Big variable process - Default Tenant',
    );
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent('1');
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /default tenant/i,
    );

    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 1, tenantId: 'tenant-b'}),
      ]),
    );
    await selectTenant({user, option: 'Tenant B'});

    expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('');
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent(/select a process version/i);
  });
});
