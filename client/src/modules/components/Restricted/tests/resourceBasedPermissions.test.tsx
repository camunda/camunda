/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Restricted} from '../index';
import {render, screen} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {groupedProcessesMock} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes';

const createWrapper = (initialPath: string = '/processes') => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        authenticationStore.reset();
        processesStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/processes" element={children} />
        </Routes>
      </MemoryRouter>
    );
  };

  return Wrapper;
};

describe('Restricted', () => {
  beforeEach(() => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };
  });

  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should show restricted content if user has write permissions and no restricted resource based scopes defined', async () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    await processesStore.fetchProcesses();

    const {rerender} = render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()}
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: [],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should hide restricted content if user has resource based permissions but no write permission ', async () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    await processesStore.fetchProcesses();

    render(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['UPDATE_PROCESS_INSTANCE'],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()}
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render restricted content for users with write permissions when resource based permissions are disabled', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: false,
    };

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()}
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render restricted content in processes page', async () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    await processesStore.fetchProcesses();

    const {rerender} = render(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['UPDATE_PROCESS_INSTANCE'],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()}
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: processesStore.getPermissions('demoProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();

    rerender(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: processesStore.getPermissions(
            'eventBasedGatewayProcess'
          ),
        }}
      >
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['DELETE_PROCESS_INSTANCE'],
          permissions: processesStore.getPermissions('bigVarProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['DELETE_PROCESS_INSTANCE'],
          permissions: processesStore.getPermissions('orderProcess'),
        }}
      >
        <div>test content</div>
      </Restricted>
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });
});
