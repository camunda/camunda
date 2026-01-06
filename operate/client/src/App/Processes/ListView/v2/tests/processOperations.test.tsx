/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ListView} from '..';
import {createWrapper} from './mocks';
import {
  mockProcessDefinitions,
  mockProcessXML,
  createUser,
  mockProcessInstancesV2,
} from 'modules/testUtils';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';

describe('<ListView /> - operations', () => {
  beforeEach(() => {
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
    });
    mockMe().withSuccess(createUser());
  });

  it('should show delete button when version is selected', async () => {
    mockSearchProcessInstances().withSuccess(mockProcessInstancesV2);
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const queryString =
      '?active=true&incidents=true&process=demoProcess&version=1';

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
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

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
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const queryString = '?active=true&incidents=true&process=demoProcess';

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
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const queryString = '?process=demoProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
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
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const queryString = '?process=demoProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
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
