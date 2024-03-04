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

import {render, screen} from 'modules/testing-library';
import {createUser} from 'modules/testUtils';

import {mockGetUser} from 'modules/mocks/api/getUser';
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
    mockGetUser().withSuccess(
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
    mockGetUser().withSuccess(
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
    mockGetUser().withSuccess(
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
