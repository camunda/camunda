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

import {render, screen, waitFor} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
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
    await processXmlStore.fetchProcessXml('bigVarProcess');
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
