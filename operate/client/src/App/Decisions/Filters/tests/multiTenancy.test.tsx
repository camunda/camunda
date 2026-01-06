/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

function getWrapper(initialPath: string = Paths.decisions()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return groupedDecisionsStore.reset;
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          <AppHeader />
          {children}
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
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
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-A', name: 'Tenant A'},
        ],
      }),
    );

    await groupedDecisionsStore.fetchDecisions();
  });

  it('should write filters to url', async () => {
    vi.stubGlobal('clientConfig', {
      multiTenancyEnabled: true,
    });

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
  });

  it('initialise filter values from url', async () => {
    vi.stubGlobal('clientConfig', {
      multiTenancyEnabled: true,
    });

    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    expect(screen.getByLabelText('Name')).toHaveValue(
      'Assign Approver Group for tenant A',
    );
    expectVersion('2');

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Tenant'})).toHaveTextContent(
        'Tenant A',
      ),
    );
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
  });

  it('should clear decision name and version field when tenant filter is changed', async () => {
    vi.stubGlobal('clientConfig', {
      multiTenancyEnabled: true,
    });

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
  });
});
