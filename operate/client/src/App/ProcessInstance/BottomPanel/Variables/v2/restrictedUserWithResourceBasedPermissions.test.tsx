/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from './index';
import {getWrapper, mockProcessInstance} from './mocks';
import {createInstance, createVariable} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

const instanceMock = createInstance({id: '1'});
jest.mock('modules/feature-flags', () => ({
  ...jest.requireActual('modules/feature-flags'),
  IS_PROCESS_INSTANCE_V2_ENABLED: true,
}));

describe('Restricted user with resource based permissions', () => {
  beforeEach(() => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(instanceMock);
    mockFetchProcessInstanceDeprecated().withSuccess(instanceMock);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  afterEach(() => {
    window.clientConfig = undefined;
  });

  beforeAll(() => {
    authenticationStore.setUser({
      displayName: 'demo',
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });
  });

  afterAll(() => {
    authenticationStore.reset();
  });

  it('should display add/edit variable buttons when update process instance permission is available', async () => {
    processInstanceDetailsStore.setProcessInstance({
      ...instanceMock,
      permissions: ['UPDATE_PROCESS_INSTANCE'],
    });

    mockFetchVariables().withSuccess([createVariable()]);
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

    render(<Variables />, {wrapper: getWrapper()});
    expect(await screen.findByTestId('variables-list')).toBeTruthy();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(await screen.findByRole('button', {name: /edit variable/i}));
  });

  it('should not display add/edit variable buttons when update process instance permission is not available', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable()]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: getWrapper()});

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

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: getWrapper()});
    expect(await screen.findByTestId('variables-list')).toBeTruthy();

    expect(
      await screen.findByRole('button', {
        name: 'View full value of testVariableName',
      }),
    ).toBeInTheDocument();
  });
});
