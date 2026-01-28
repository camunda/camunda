/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {createUser} from 'modules/testUtils';

import {mockMe} from 'modules/mocks/api/v2/me';
import {TenantField} from '.';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';
import {type ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

function getWrapper(initialValues?: ProcessInstanceFilters) {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <Form
          onSubmit={() => {}}
          mutators={{...arrayMutators}}
          initialValues={initialValues}
        >
          {({handleSubmit}) => {
            return <form onSubmit={handleSubmit}>{children}</form>;
          }}
        </Form>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('Tenant Field', () => {
  it('should only contain all tenants filter', async () => {
    mockMe().withSuccess(
      createUser({
        tenants: [],
      }),
    );

    const {user} = render(<TenantField />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('combobox', {name: 'Select a tenant'}));

    expect(screen.getAllByRole('option')).toHaveLength(1);
    expect(
      screen.getByRole('option', {name: 'All tenants'}),
    ).toBeInTheDocument();
  });

  it('should contain list of tenants', async () => {
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-A', name: 'Tenant A'},
          {key: 3, tenantId: 'tenant-B', name: 'Tenant B'},
        ],
      }),
    );

    const {user} = render(<TenantField />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('combobox', {name: 'Select a tenant'}));

    await waitFor(() => {
      expect(screen.getAllByRole('option')).toHaveLength(4);
    });
    expect(
      screen.getByRole('option', {name: 'All tenants'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', {name: 'Default Tenant'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('option', {name: 'Tenant A'})).toBeInTheDocument();
    expect(screen.getByRole('option', {name: 'Tenant B'})).toBeInTheDocument();
  });

  it('should not set value if its not valid', async () => {
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-A', name: 'Tenant A'},
          {key: 3, tenantId: 'tenant-B', name: 'Tenant B'},
        ],
      }),
    );

    render(<TenantField />, {
      wrapper: getWrapper({tenant: 'invalid-tenant'}),
    });

    expect(
      screen.getByRole('combobox', {name: 'Select a tenant'}),
    ).toHaveTextContent('Select a tenant');
  });
});
