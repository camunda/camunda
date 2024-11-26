/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {createUser} from 'modules/testUtils';

import {mockMe} from 'modules/mocks/api/v2/me';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {TenantField} from '.';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';
import {ProcessInstanceFilters} from 'modules/utils/filter/shared';

function getWrapper(initialValues?: ProcessInstanceFilters) {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => {
    useEffect(() => {
      return () => {
        authenticationStore.reset();
      };
    }, []);

    return (
      <Form
        onSubmit={() => {}}
        mutators={{...arrayMutators}}
        initialValues={initialValues}
      >
        {({handleSubmit}) => {
          return <form onSubmit={handleSubmit}>{children}</form>;
        }}
      </Form>
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

    await authenticationStore.authenticate();

    const {user} = render(<TenantField />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('combobox', {name: 'Tenant'}));

    expect(screen.getAllByRole('option')).toHaveLength(1);
    expect(
      screen.getByRole('option', {name: 'All tenants'}),
    ).toBeInTheDocument();
  });

  it('should contain list of tenants', async () => {
    mockMe().withSuccess(
      createUser({
        tenants: [
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: 'tenant-A', name: 'Tenant A'},
          {tenantId: 'tenant-B', name: 'Tenant B'},
        ],
      }),
    );

    await authenticationStore.authenticate();

    const {user} = render(<TenantField />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('combobox', {name: 'Tenant'}));

    expect(screen.getAllByRole('option')).toHaveLength(4);
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
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: 'tenant-A', name: 'Tenant A'},
          {tenantId: 'tenant-B', name: 'Tenant B'},
        ],
      }),
    );

    await authenticationStore.authenticate();

    render(<TenantField />, {
      wrapper: getWrapper({tenant: 'invalid-tenant'}),
    });

    expect(screen.getByRole('combobox', {name: 'Tenant'})).toHaveTextContent(
      'Select a tenant',
    );
  });
});
