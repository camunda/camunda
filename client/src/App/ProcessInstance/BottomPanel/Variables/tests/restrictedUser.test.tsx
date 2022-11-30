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
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {createInstance, createVariable} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const instanceMock = createInstance({id: '1'});

describe('Restricted user', () => {
  beforeEach(() => {
    flowNodeSelectionStore.init();
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    variablesStore.reset();
    flowNodeSelectionStore.reset();
    processInstanceDetailsStatisticsStore.reset();
    modificationsStore.reset();
  });

  beforeAll(() => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
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

  it('should not display Edit Variable button', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable()]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.queryByTitle(/enter edit mode/i)).not.toBeInTheDocument();
  });

  it('should not display Add Variable footer', async () => {
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
  });

  it('should have a button to see full variable value', async () => {
    processInstanceDetailsStore.setProcessInstance({
      ...instanceMock,
    });

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
