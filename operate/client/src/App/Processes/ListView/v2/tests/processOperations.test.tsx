/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ListView} from '..';
import {createSimpleV2TestWrapper} from './testUtils';
import {
  mockProcessDefinitions,
  mockProcessXML,
  createUser,
  mockProcessInstancesV2,
  searchResult,
  createProcessDefinition,
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
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({
          processDefinitionId: 'demoProcess',
          processDefinitionKey: 'demoProcess1',
          name: 'New demo process',
          version: 1,
        }),
      ]),
    );
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

    render(<ListView />, {
      wrapper: createSimpleV2TestWrapper(`/processes${queryString}`),
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
      wrapper: createSimpleV2TestWrapper('/processes'),
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

    render(<ListView />, {
      wrapper: createSimpleV2TestWrapper(`/processes${queryString}`),
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

  it.skip('should not show delete button when user has no resource based permissions', async () => {
    // This test is skipped because resourcePermissionsEnabled is deprecated.
    // Per the @deprecated comment in global.d.ts:
    // "The C8 API does not expose permissions with resources.
    // Therefore, permissions should not be checked proactively on the client.
    // Let users try the action and surface missing permissions errors instead."
    //
    // The v2 implementation correctly shows the delete button and handles
    // permission errors from the server when the user attempts the action.

    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const queryString = '?process=demoProcess&version=1';

    vi.stubGlobal('clientConfig', {
      resourcePermissionsEnabled: true,
    });

    render(<ListView />, {
      wrapper: createSimpleV2TestWrapper(`/processes${queryString}`),
    });

    expect(
      await screen.findByRole('heading', {name: 'New demo process'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /delete process definition/i,
      }),
    ).not.toBeInTheDocument();
  });
});
