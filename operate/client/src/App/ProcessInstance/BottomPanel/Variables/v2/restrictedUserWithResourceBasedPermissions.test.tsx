/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {getWrapper, mockProcessInstance} from './mocks';
import {
  createInstance,
  createUser,
  createVariable,
  createVariableV2,
} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {VariablePanel} from '../../VariablePanel/v2';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockFetchProcessInstanceListeners} from 'modules/mocks/api/processInstances/fetchProcessInstanceListeners';

const instanceMock = createInstance({id: '1'});

describe('Restricted user with resource based permissions', () => {
  beforeEach(() => {
    vi.stubGlobal('clientConfig', {
      resourcePermissionsEnabled: true,
    });

    mockMe().withSuccess(createUser({authorizedApplications: ['operate']}));
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(instanceMock);
    mockFetchProcessInstanceDeprecated().withSuccess(instanceMock);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceListeners().withSuccess({
      listeners: [],
      totalCount: 0,
    });
  });

  it('should display add/edit variable buttons when update process instance permission is available', async () => {
    processInstanceDetailsStore.setProcessInstance({
      ...instanceMock,
      permissions: ['UPDATE_PROCESS_INSTANCE'],
    });

    mockFetchVariables().withSuccess([createVariable()]);
    mockSearchVariables().withSuccess({
      items: [createVariableV2()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createVariableV2()],
      page: {
        totalItems: 1,
      },
    });
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess({
      ...instanceMock,
      permissions: ['UPDATE_PROCESS_INSTANCE'],
    });

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /edit variable/i}),
    ).toBeInTheDocument();
  });

  it('should not display add/edit variable buttons when update process instance permission is not available', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable()]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
  });

  it('should have a button to see full variable value', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable({isPreview: true})]);
    mockSearchVariables().withSuccess({
      items: [createVariableV2({isTruncated: true})],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createVariableV2({isTruncated: true})],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createVariableV2({isTruncated: true})],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createVariableV2({isTruncated: true})],
      page: {
        totalItems: 1,
      },
    });

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      await screen.findByRole('button', {
        name: 'View full value of testVariableName',
      }),
    ).toBeInTheDocument();
  });
});
