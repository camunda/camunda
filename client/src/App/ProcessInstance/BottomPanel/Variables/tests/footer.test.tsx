/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from '../index';
import {Wrapper, mockVariables} from './mocks';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {createInstance} from 'modules/testUtils';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {act} from 'react-dom/test-utils';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const instanceMock = createInstance({id: '1'});

describe('Footer', () => {
  it('should disable add variable button when loading', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});

    expect(screen.getByText(/add variable/i)).toBeDisabled();
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(screen.getByText(/add variable/i)).toBeEnabled();
  });

  it('should disable add variable button if instance state is cancelled', async () => {
    processInstanceDetailsStore.setProcessInstance({
      ...instanceMock,
      state: 'CANCELED',
    });

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await waitFor(() =>
      expect(screen.getByText(/add variable/i)).toBeDisabled()
    );
  });

  it('should hide/disable add variable button if add/edit variable button is clicked', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByText(/add variable/i)).not.toBeInTheDocument();

    await user.click(screen.getByTitle(/exit edit mode/i));
    expect(screen.getByText(/add variable/i)).toBeEnabled();

    const [firstEditVariableButton] = screen.getAllByTestId(
      'edit-variable-button'
    );
    expect(firstEditVariableButton).toBeInTheDocument();
    await user.click(firstEditVariableButton!);
    expect(screen.getByText(/add variable/i)).toBeDisabled();

    await user.click(screen.getByTitle(/exit edit mode/i));
    expect(screen.getByText(/add variable/i)).toBeEnabled();
  });

  it('should disable add variable button when selected flow node is not running', async () => {
    processInstanceDetailsStatisticsStore.init(instanceMock.id);
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'start',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'neverFails',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ]);
    mockFetchVariables().withSuccess([]);

    flowNodeMetaDataStore.init();
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByText(/add variable/i)).toBeEnabled();

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    act(() =>
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'start',
        flowNodeInstanceId: '2',
        isMultiInstance: false,
      })
    );

    await waitFor(() =>
      expect(
        flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate
      ).toEqual(null)
    );

    expect(screen.getByText(/add variable/i)).toBeEnabled();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() =>
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'neverFails',
        flowNodeInstanceId: '3',
        isMultiInstance: false,
      })
    );

    await waitFor(() =>
      expect(
        flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate
      ).toEqual(MOCK_TIMESTAMP)
    );

    expect(screen.getByText(/add variable/i)).toBeDisabled();

    flowNodeMetaDataStore.reset();
  });
});
