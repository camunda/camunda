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

import {AppHeader} from 'App/Layout/AppHeader';
import {render, screen, waitFor, within} from 'modules/testing-library';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from '../';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {useEffect} from 'react';
import {
  selectDecision,
  selectTenant,
} from 'modules/testUtils/selectComboBoxOption';
import {Paths} from 'modules/Routes';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';

function reset() {
  jest.clearAllTimers();
  jest.useRealTimers();
}

function getWrapper(initialPath: string = Paths.decisions()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return groupedDecisionsStore.reset;
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <AppHeader />
        {children}
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

const expectVersion = (version: string) => {
  expect(
    within(screen.getByLabelText('Version', {selector: 'button'})).getByText(
      version,
    ),
  ).toBeInTheDocument();
};

const MOCK_FILTERS_PARAMS = {
  name: 'invoice-assign-approver',
  version: '2',
  evaluated: 'true',
  failed: 'true',
  decisionInstanceIds: '2251799813689540-1',
  processInstanceId: '2251799813689549',
  tenant: 'tenant-A',
} as const;

describe('<Filters />', () => {
  beforeEach(async () => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
    mockGetUser().withSuccess(
      createUser({
        tenants: [
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: 'tenant-A', name: 'Tenant A'},
        ],
      }),
    );

    await authenticationStore.authenticate();

    await groupedDecisionsStore.fetchDecisions();
    jest.useFakeTimers();
  });

  afterEach(reset);

  it('should write filters to url', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    const MOCK_VALUES = {
      name: 'invoice-assign-approver',
      version: '3',
      tenant: 'tenant-A',
    } as const;

    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
    await selectTenant({user, option: 'All tenants'});

    await waitFor(() => expect(screen.getByLabelText('Name')).toBeEnabled());

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

    await selectDecision({
      user,
      option: 'Assign Approver Group for tenant A - Tenant A',
    });

    expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
      'Assign Approver Group for tenant A',
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
      ).toEqual(MOCK_VALUES),
    );

    window.clientConfig = undefined;
  });

  it('initialise filter values from url', () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    expect(screen.getByLabelText('Name')).toHaveValue(
      'Assign Approver Group for tenant A',
    );
    expectVersion('2');

    expect(screen.getByRole('combobox', {name: 'Tenant'})).toHaveTextContent(
      'Tenant A',
    );

    window.clientConfig = undefined;
  });

  it('should hide multi tenancy filter if its not enabled in client config', async () => {
    render(<Filters />, {
      wrapper: getWrapper(
        `/?${new URLSearchParams(
          Object.entries(MOCK_FILTERS_PARAMS),
        ).toString()}`,
      ),
    });

    expect(
      screen.queryByRole('combobox', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should disable decision name field when tenant is not selected', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched'),
    );
    expect(screen.getByLabelText('Name')).toBeDisabled();

    window.clientConfig = undefined;
  });

  it('should clear decision name and version field when tenant filter is changed', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched'),
    );
    expect(screen.getByLabelText('Name')).toBeDisabled();

    await selectTenant({user, option: 'All tenants'});
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /all tenants/i,
    );

    await waitFor(() => expect(screen.getByLabelText('Name')).toBeEnabled());

    await selectDecision({
      user,
      option: 'Assign Approver Group - Default Tenant',
    });

    expect(screen.getByLabelText('Name')).toHaveValue('Assign Approver Group');
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent('2');
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /default tenant/i,
    );

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
    await selectTenant({user, option: 'Tenant A'});
    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched'),
    );

    expect(screen.getByLabelText('Name')).toHaveValue('');
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent(/select a decision version/i);

    window.clientConfig = undefined;
  });
});
