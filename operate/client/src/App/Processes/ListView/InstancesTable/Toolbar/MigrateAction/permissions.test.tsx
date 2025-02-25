/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
