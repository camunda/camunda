/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {processesStore} from 'modules/stores/processes';
import {processDiagramStore} from 'modules/stores/processDiagram';
import {
  createUser,
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {
  selectProcess,
  selectTenant,
} from 'modules/testUtils/selectComboBoxOption';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {authenticationStore} from 'modules/stores/authentication';

jest.unmock('modules/utils/date/formatDate');

describe('Filters', () => {
  beforeEach(async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockGetUser().withSuccess(
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
    await processDiagramStore.fetchProcessDiagram('bigVarProcess');
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

    // Wait for data to be fetched
    await waitFor(() => expect(screen.getByLabelText('Name')).toBeEnabled());

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
});
