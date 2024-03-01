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

import {render, screen} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {ListView} from '..';
import {createWrapper} from './mocks';
import {
  groupedProcessesMock,
  mockProcessXML,
  operations,
} from 'modules/testUtils';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';

describe('<ListView /> - operations', () => {
  const originalWindow = {...window};
  const locationSpy = jest.spyOn(window, 'location', 'get');

  beforeEach(() => {
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess([]);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchBatchOperations().withSuccess(operations);
  });

  afterEach(() => {
    locationSpy.mockClear();
  });

  it('should show delete button when version is selected', async () => {
    const queryString = '?process=demoProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    render(<ListView />, {
      wrapper: createWrapper(`/processes${queryString}`),
    });

    expect(
      await screen.findByRole('heading', {name: 'New demo process'}),
    ).toBeInTheDocument();

    expect(
      await screen.findByRole('heading', {
        name: 'Process Instances',
      }),
    );
    expect(
      await screen.findByRole('button', {
        name: /^delete process definition "new demo process - version 1"$/i,
      }),
    ).toBeInTheDocument();
  });

  it('should not show delete button when no process is selected', async () => {
    render(<ListView />, {
      wrapper: createWrapper('/processes'),
    });

    expect(
      await screen.findByText('There is no Process selected'),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', {
        name: 'Process Instances',
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when no version is selected', async () => {
    const queryString = '?process=demoProcess';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    render(<ListView />, {
      wrapper: createWrapper(`/processes${queryString}`),
    });

    expect(
      await screen.findByRole('heading', {name: 'New demo process'}),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', {
        name: 'Process Instances',
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when user has no permissions', async () => {
    const queryString = '?process=demoProcess';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

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

    render(<ListView />, {
      wrapper: createWrapper(`/processes${queryString}`),
    });

    expect(
      await screen.findByRole('heading', {name: 'New demo process'}),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', {
        name: 'Process Instances',
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when user has no resource based permissions', async () => {
    const queryString = '?process=demoProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

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

    const {rerender} = render(<ListView />, {
      wrapper: createWrapper(`/processes${queryString}`),
    });

    expect(
      await screen.findByRole('button', {
        name: /delete process definition/i,
      }),
    ).toBeInTheDocument();
    expect(await screen.findByRole('button', {name: 'Zoom in diagram'}));

    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    rerender(<ListView />);

    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();

    window.clientConfig = undefined;
  });
});
