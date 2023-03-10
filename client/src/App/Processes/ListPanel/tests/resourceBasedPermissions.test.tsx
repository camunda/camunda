/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {ListPanel} from '../index';
import {processInstancesStore} from 'modules/stores/processInstances';
import {authenticationStore} from 'modules/stores/authentication';
import {createWrapper} from './mocks';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {createInstance} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes';
import {ProcessDto} from 'modules/api/processes/fetchGroupedProcesses';

const runningInstanceWithUpdatePermissions = createInstance({
  bpmnProcessId: 'processWithUpdatePermissions',
  processName: 'process with update permissions',
});

const runningInstanceWithoutUpdatePermissions = createInstance({
  bpmnProcessId: 'processWithoutUpdatePermissions',
  processName: 'process without update permissions',
});

const finishedInstanceWithDeletePermissions = createInstance({
  bpmnProcessId: 'processWithDeletePermissions',
  processName: 'process with delete permissions',
  state: 'COMPLETED',
});

const finishedInstanceWithoutDeletePermissions = createInstance({
  bpmnProcessId: 'processWithoutDeletePermissions',
  processName: 'process without delete permissions',
  state: 'COMPLETED',
});

const mockGroupedProcesses: ProcessDto[] = [
  {
    bpmnProcessId: 'processWithUpdatePermissions',
    name: 'process with update permissions',
    processes: [
      {
        id: '1',
        name: 'process with update permissions',
        version: 1,
        bpmnProcessId: 'processWithUpdatePermissions',
      },
    ],
    permissions: ['READ', 'UPDATE_PROCESS_INSTANCE'],
  },
  {
    bpmnProcessId: 'processWithoutUpdatePermissions',
    name: 'process without update permissions',
    processes: [
      {
        id: '2',
        name: 'process without update permissions',
        version: 1,
        bpmnProcessId: 'processWithoutUpdatePermissions',
      },
    ],
    permissions: ['READ', 'DELETE_PROCESS_INSTANCE'],
  },
  {
    bpmnProcessId: 'processWithDeletePermissions',
    name: 'process with delete permissions',
    processes: [
      {
        id: '3',
        name: 'process with update permissions',
        version: 1,
        bpmnProcessId: 'processWithDeletePermissions',
      },
    ],
    permissions: ['READ', 'DELETE_PROCESS_INSTANCE'],
  },
  {
    bpmnProcessId: 'processWithoutDeletePermissions',
    name: 'process without delete permissions',
    processes: [
      {
        id: '4',
        name: 'process without delete permissions',
        version: 1,
        bpmnProcessId: 'processWithoutDeletePermissions',
      },
    ],
    permissions: ['READ', 'UPDATE_PROCESS_INSTANCE'],
  },
];

describe('Display operation buttons depending on resource based permissions', () => {
  beforeEach(async () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchGroupedProcesses().withSuccess(mockGroupedProcesses);
    await processesStore.fetchProcesses();

    mockFetchProcessInstances().withSuccess({
      processInstances: [
        runningInstanceWithUpdatePermissions,
        runningInstanceWithoutUpdatePermissions,
        finishedInstanceWithDeletePermissions,
        finishedInstanceWithoutDeletePermissions,
      ],
      totalCount: 4,
    });

    processInstancesStore.fetchProcessInstancesFromFilters();
  });

  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should render operation button when update permission is available for the process', async () => {
    render(<ListPanel />, {
      wrapper: createWrapper(
        '/processes?incidents=true&active=true&completed=true&canceled=true'
      ),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
    expect(screen.getByText('Operations')).toBeInTheDocument();

    expect(
      within(
        screen.getByRole('row', {
          name: `Instance ${runningInstanceWithUpdatePermissions.id}`,
        })
      ).getByRole('button', {
        name: `Cancel Instance ${runningInstanceWithUpdatePermissions.id}`,
      })
    ).toBeInTheDocument();
  });

  it('should render operation button when update permission is not available for the process', async () => {
    render(<ListPanel />, {
      wrapper: createWrapper(
        '/processes?incidents=true&active=true&completed=true&canceled=true'
      ),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
    expect(screen.getByText('Operations')).toBeInTheDocument();

    expect(
      within(
        screen.queryByRole('row', {
          name: `Instance ${runningInstanceWithoutUpdatePermissions.id}`,
        })!
      ).queryByRole('button', {
        name: `Cancel Instance ${runningInstanceWithoutUpdatePermissions.id}`,
      })
    ).not.toBeInTheDocument();
  });

  it('should render operation button when delete process instance permission is available for the process', async () => {
    render(<ListPanel />, {
      wrapper: createWrapper(
        '/processes?incidents=true&active=true&completed=true&canceled=true'
      ),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
    expect(screen.getByText('Operations')).toBeInTheDocument();
    expect(
      within(
        screen.getByRole('row', {
          name: `Instance ${finishedInstanceWithDeletePermissions.id}`,
        })
      ).getByRole('button', {
        name: `Delete Instance ${finishedInstanceWithDeletePermissions.id}`,
      })
    ).toBeInTheDocument();
  });

  it('should render operation button when delete process instance permission is not available for the process', async () => {
    render(<ListPanel />, {
      wrapper: createWrapper(
        '/processes?incidents=true&active=true&completed=true&canceled=true'
      ),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
    expect(screen.getByText('Operations')).toBeInTheDocument();

    expect(
      within(
        screen.queryByRole('row', {
          name: `Instance ${finishedInstanceWithoutDeletePermissions.id}`,
        })!
      ).queryByRole('button', {
        name: `Delete Instance ${finishedInstanceWithoutDeletePermissions.id}`,
      })
    ).not.toBeInTheDocument();
  });
});
