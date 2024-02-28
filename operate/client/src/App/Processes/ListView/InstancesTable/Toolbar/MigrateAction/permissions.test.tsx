/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import {MigrateAction} from '.';
import {MemoryRouter} from 'react-router-dom';
import {useEffect} from 'react';
import {authenticationStore} from 'modules/stores/authentication';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {processesStore} from 'modules/stores/processes/processes.list';
import {groupedProcessesMock} from 'modules/testUtils';
import {Paths} from 'modules/Routes';

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        authenticationStore.reset();
        processesStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
    );
  };

  return Wrapper;
}

describe('<MigrateAction /> - permissions', () => {
  it('should render migrate button when resource based permissions are not enabled', () => {
    render(<MigrateAction />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: 'Migrate'})).toBeInTheDocument();
  });

  it('should not render, when the user does not have write permissions', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<MigrateAction />, {wrapper: getWrapper()});

    expect(
      screen.queryByRole('button', {name: 'Migrate'}),
    ).not.toBeInTheDocument();
  });

  describe('resource based permissions', () => {
    beforeEach(() => {
      window.clientConfig = {
        resourcePermissionsEnabled: true,
      };
    });

    afterEach(() => {
      window.clientConfig = undefined;
    });

    it('should not render migrate button when resource based permissions are enabled without permission', async () => {
      mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
      await processesStore.fetchProcesses();

      render(<MigrateAction />, {
        wrapper: getWrapper(
          '/processes?process=eventBasedGatewayProcess&version=1',
        ),
      });
      expect(
        screen.queryByRole('button', {name: 'Migrate'}),
      ).not.toBeInTheDocument();
    });

    it('should render migrate button when resource based permissions are enabled with permission', async () => {
      mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
      await processesStore.fetchProcesses();

      render(<MigrateAction />, {
        wrapper: getWrapper('/processes?process=demoProcess&version=1'),
      });
      expect(screen.getByRole('button', {name: 'Migrate'})).toBeInTheDocument();
    });
  });
});
