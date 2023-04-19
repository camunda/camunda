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
import Variables from '../index';
import {Wrapper, mockVariables, mockMetaData} from './mocks';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';

const EMPTY_PLACEHOLDER = 'The Flow Node has no Variables';

const mockDisplayNotification = jest.fn();

jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

describe('Skeleton', () => {
  it('should display empty content if there are no variables', async () => {
    mockFetchVariables().withSuccess([]);
    flowNodeMetaDataStore.setMetaData(mockMetaData);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});

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
