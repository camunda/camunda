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

  it('should not show delete button when user has no resource based permissions', async () => {
    const queryString = '?process=demoProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    authenticationStore.setUser({
      displayName: 'demo',
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
