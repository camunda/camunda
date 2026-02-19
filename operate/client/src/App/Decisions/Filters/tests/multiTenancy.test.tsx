/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AppHeader} from 'App/Layout/AppHeader';
import {render, screen, waitFor, within} from 'modules/testing-library';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter} from 'react-router-dom';
import {Filters} from '../';
import {
  selectDecision,
  selectTenant,
} from 'modules/testUtils/selectComboBoxOption';
import {Paths} from 'modules/Routes';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser, searchResult} from 'modules/testUtils';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockSearchDecisionDefinitions} from 'modules/mocks/api/v2/decisionDefinitions/searchDecisionDefinitions';
import {createDecisionDefinition} from 'modules/mocks/mockDecisionDefinitions';
import {getClientConfig} from 'modules/utils/getClientConfig';

vi.mock('modules/utils/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('modules/utils/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('modules/utils/getClientConfig')
>('modules/utils/getClientConfig');

const mockGetClientConfig = vi.mocked(getClientConfig);

function getWrapper(initialPath: string = Paths.decisions()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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
    within(
      screen.getByRole('combobox', {
        name: /select a decision version/i,
      }),
    ).getByText(version),
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
    mockGetClientConfig.mockReturnValue(actualGetClientConfig());
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([
        createDecisionDefinition({
          version: 2,
          tenantId: 'tenant-A',
          name: 'Assign Approver Group for tenant A',
        }),
        createDecisionDefinition({
          version: 1,
          tenantId: 'tenant-A',
          name: 'Assign Approver Group for tenant A',
        }),
      ]),
    );
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([
        createDecisionDefinition({
          version: 2,
          tenantId: 'tenant-A',
          name: 'Assign Approver Group for tenant A',
        }),
      ]),
    );
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([
        createDecisionDefinition({
          version: 2,
          tenantId: 'tenant-A',
          name: 'Assign Approver Group for tenant A',
        }),
      ]),
    );
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-A', name: 'Tenant A'},
        ],
      }),
    );
  });

  it('should write filters to url', async () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      multiTenancyEnabled: true,
    });

    const MOCK_VALUES = {
      name: 'invoice-assign-approver',
      version: '2',
      tenant: 'tenant-A',
    } as const;

    const {user} = render(<Filters />, {wrapper: getWrapper()});

    await selectTenant({user, option: 'All tenants'});

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toBeEnabled(),
    );
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
      ).toEqual(expect.objectContaining({tenant: 'all'})),
    );

    await selectDecision({
      user,
      option: 'Assign Approver Group for tenant A - Tenant A',
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
        'Assign Approver Group for tenant A',
      ),
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

  it('initialize filter values from url', async () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      multiTenancyEnabled: true,
    });

    render(<Filters />, {
      wrapper: getWrapper(`/?${new URLSearchParams(MOCK_FILTERS_PARAMS)}`),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
        'Assign Approver Group for tenant A',
      ),
    );
    expectVersion('2');

    await waitFor(() =>
      expect(
        screen.getByRole('combobox', {name: /Select a tenant/i}),
      ).toHaveTextContent('Tenant A'),
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
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      multiTenancyEnabled: true,
    });
    render(<Filters />, {wrapper: getWrapper()});

    expect(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();
  });

  it('should clear decision name and version field when tenant filter is changed', async () => {
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([createDecisionDefinition()]),
    );
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([createDecisionDefinition()]),
    );
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      multiTenancyEnabled: true,
    });
    const {user} = render(<Filters />, {wrapper: getWrapper()});

    expect(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();

    await selectTenant({user, option: 'All tenants'});
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /all tenants/i,
    );

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toBeEnabled(),
    );

    await selectDecision({
      user,
      option: 'Assign Approver Group - Default Tenant',
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
        'Assign Approver Group',
      ),
    );
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent('2');
    expect(screen.getByRole('combobox', {name: /tenant/i})).toHaveTextContent(
      /default tenant/i,
    );

    await selectTenant({user, option: 'Tenant A'});

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(''),
    );
    expect(
      screen.getByLabelText('Version', {selector: 'button'}),
    ).toHaveTextContent(/select a decision version/i);
  });
});
