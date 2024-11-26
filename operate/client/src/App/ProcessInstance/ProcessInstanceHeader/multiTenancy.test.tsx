/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {ProcessInstanceHeader} from './index';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {operationsStore} from 'modules/stores/operations';
import {mockInstanceWithoutOperations} from './index.setup';
import {MemoryRouter} from 'react-router-dom';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {createUser, mockProcessXML} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {mockMe} from 'modules/mocks/api/v2/me';

jest.mock('modules/stores/process', () => ({
  processStore: {state: {process: {}}, fetchProcess: jest.fn()},
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    authenticationStore.authenticate();

    return () => {
      operationsStore.reset();
      processInstanceDetailsStore.reset();
      processInstanceDetailsDiagramStore.reset();
      authenticationStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      {children}
    </MemoryRouter>
  );
};

describe('InstanceHeader', () => {
  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should render multi tenancy column and include tenant in version link', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithoutOperations.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(await screen.findByText('Default Tenant')).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: 'View process "someProcessName version 1" instances - Default Tenant',
      }),
    ).toHaveAttribute(
      'href',
      `${Paths.processes()}?${new URLSearchParams({
        version: '1',
        process: 'someKey',
        active: 'true',
        incidents: 'true',
        tenant: '<default>',
      })}`,
    );
  });

  it('should hide multi tenancy column and exclude tenant from version link', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithoutOperations.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Default Tenant')).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: 'View process "someProcessName version 1" instances',
      }),
    ).toBeInTheDocument();
  });
});
