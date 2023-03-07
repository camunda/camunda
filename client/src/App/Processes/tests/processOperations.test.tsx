/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {Processes} from '..';
import {createWrapper} from './mocks';
import {
  groupedProcessesMock,
  mockProcessInstances,
  mockProcessXML,
  operations,
} from 'modules/testUtils';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';

jest.mock('modules/feature-flags', () => ({
  IS_PROCESS_DEFINITION_DELETION_ENABLED: true,
}));

describe('<Processes /> - operations', () => {
  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess([]);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchBatchOperations().withSuccess(operations);
  });

  it('should show delete button when version is selected', async () => {
    render(<Processes />, {
      wrapper: createWrapper('/processes?process=demoProcess&version=1'),
    });

    expect(
      await screen.findByRole('button', {
        name: /^delete process definition "new demo process - version 1"$/i,
      })
    ).toBeInTheDocument();
  });

  it('should not show delete button when no process is selected', () => {
    render(<Processes />, {
      wrapper: createWrapper('/processes'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when no version is selected', () => {
    render(<Processes />, {
      wrapper: createWrapper('/processes?process=demoProcess'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when user has no permissions', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<Processes />, {
      wrapper: createWrapper('/processes?process=demoProcess&version=1'),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when user has no resource based permissions', async () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    const {rerender} = render(<Processes />, {
      wrapper: createWrapper('/processes?process=demoProcess&version=1'),
    });

    expect(
      await screen.findByRole('button', {
        name: /delete process definition/i,
      })
    ).toBeInTheDocument();
    expect(await screen.findByRole('button', {name: 'Zoom in diagram'}));

    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    rerender(<Processes />);

    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      })
    ).not.toBeInTheDocument();

    window.clientConfig = undefined;
  });
});
