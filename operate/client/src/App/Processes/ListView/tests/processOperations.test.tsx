/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';

describe('<ListView /> - operations', () => {
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
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchBatchOperations().withSuccess(operations);
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
    });
  });

  it('should show delete button when version is selected', async () => {
    const queryString = '?process=demoProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
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
    ).toBeInTheDocument();
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
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should not show delete button when no version is selected', async () => {
    const queryString = '?process=demoProcess';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
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
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should show delete button when user has resource based permissions', async () => {
    const queryString = '?process=demoProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    authenticationStore.setUser({
      displayName: 'demo',
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
      await screen.findByRole('button', {
        name: /delete process definition/i,
      }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: 'Zoom in diagram'}),
    ).toBeInTheDocument();
  });

  it('should not show delete button when user has no resource based permissions', async () => {
    const queryString = '?process=demoProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    authenticationStore.setUser({
      displayName: 'demo',
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    vi.stubGlobal('clientConfig', {
      resourcePermissionsEnabled: true,
    });

    render(<ListView />, {
      wrapper: createWrapper(`/processes${queryString}`),
    });

    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });
});
