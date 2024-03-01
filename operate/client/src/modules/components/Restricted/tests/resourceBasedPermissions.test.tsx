/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {Restricted} from '../index';
import {render, screen} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {groupedProcessesMock} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.list';
import {Paths} from 'modules/Routes';

const createWrapper = (initialPath: string = Paths.processes()) => {
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
          <Route path={Paths.processes()} element={children} />
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
      tenants: [],
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    await processesStore.fetchProcesses();

    const {rerender} = render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: createWrapper()},
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
      </Restricted>,
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
      tenants: [],
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
      {wrapper: createWrapper()},
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
      tenants: [],
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
      {wrapper: createWrapper()},
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
      tenants: [],
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
      {wrapper: createWrapper()},
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
      </Restricted>,
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();

    rerender(
      <Restricted
        scopes={['write']}
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: processesStore.getPermissions(
            'eventBasedGatewayProcess',
          ),
        }}
      >
        <div>test content</div>
      </Restricted>,
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
      </Restricted>,
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
      </Restricted>,
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });
});
