/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {InstancesTable} from '.';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
    );
  };

  return Wrapper;
}

describe('<InstancesTable />', () => {
  it.each(['all', undefined])(
    'should show tenant column when multi tenancy is enabled and tenant filter is %p',
    async (tenant) => {
      window.clientConfig = {
        multiTenancyEnabled: true,
      };

      render(<InstancesTable />, {
        wrapper: getWrapper(
          `${Paths.processes()}?${new URLSearchParams(
            tenant === undefined ? undefined : {tenant},
          )}`,
        ),
      });

      expect(
        screen.getByRole('columnheader', {name: 'Tenant'}),
      ).toBeInTheDocument();

      window.clientConfig = undefined;
    },
  );

  it('should hide tenant column when multi tenancy is enabled and tenant filter is a specific tenant', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    render(<InstancesTable />, {
      wrapper: getWrapper(
        `${Paths.processes()}?${new URLSearchParams({tenant: 'tenant-a'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();

    window.clientConfig = undefined;
  });

  it('should hide tenant column when multi tenancy is disabled', async () => {
    render(<InstancesTable />, {
      wrapper: getWrapper(
        `${Paths.processes()}?${new URLSearchParams({tenant: 'all'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });
});
