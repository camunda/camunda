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
import {ProcessInstanceHeader} from '../index';
import {operationsStore} from 'modules/stores/operations';
import {mockInstance} from './index.setup';
import {MemoryRouter} from 'react-router-dom';
import {createUser, mockProcessXML} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import * as clientConfig from 'modules/utils/getClientConfig';

vi.mock('modules/stores/process', () => ({
  processStore: {state: {process: {}}, fetchProcess: vi.fn()},
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      operationsStore.reset();
      authenticationStore.reset();
    };
  }, []);

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          {children}
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

describe('InstanceHeader', () => {
  it('should render multi tenancy column and include tenant in version link', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });

    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
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
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {key: 1, tenantId: '<default>', name: 'Default Tenant'},
          {key: 2, tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    render(<ProcessInstanceHeader processInstance={mockInstance} />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Default Tenant')).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: 'View process "someProcessName version 1" instances',
      }),
    ).toBeInTheDocument();
  });
});
