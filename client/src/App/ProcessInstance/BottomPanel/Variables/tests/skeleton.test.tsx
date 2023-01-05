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
import {Wrapper, mockVariables, mockMetaData} from './mocks';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

const mockDisplayNotification = jest.fn();

jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

describe('Skeleton', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

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

  it('should display empty content if there are no variables', async () => {
    mockFetchVariables().withSuccess([]);

    render(<Variables />, {wrapper: Wrapper});
    flowNodeMetaDataStore.setMetaData(mockMetaData);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(await screen.findByText(EMPTY_PLACEHOLDER)).toBeInTheDocument();
  });

  it('should display skeleton on initial load', async () => {
    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});

    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
  });
});
