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
} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from '../index';
import {Wrapper} from './mocks';
import {createInstance, createVariable} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';

const instanceMock = createInstance({id: '1'});

describe('Restricted user with resource based permissions', () => {
  beforeEach(() => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };
  });

  afterEach(() => {
    window.clientConfig = undefined;
  });

  beforeAll(() => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
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

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByTitle(/enter edit mode/i)).toBeInTheDocument();
    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();
  });

  it('should not display add/edit variable buttons when update process instance permission is not available', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable()]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();
    expect(screen.queryByTitle(/enter edit mode/i)).not.toBeInTheDocument();
  });

  it('should have a button to see full variable value', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable({isPreview: true})]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() => screen.getByTestId('skeleton-rows'));

    expect(
      screen.getByTitle('View full value of testVariableName')
    ).toBeInTheDocument();
  });
});
