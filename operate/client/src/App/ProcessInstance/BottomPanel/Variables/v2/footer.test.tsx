/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from './index';
import {getWrapper, mockProcessInstance, mockVariables} from './mocks';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {
  createInstance,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {act} from 'react';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init} from 'modules/utils/flowNodeMetadata';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';

const instanceMock = createInstance({id: '1'});

describe('Footer', () => {
  afterEach(async () => {
    jest.clearAllMocks();
    await new Promise(process.nextTick);
  });

  it('should disable add variable button when selected flow node is not running', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockProcessInstanceDeprecated().withSuccess(instanceMock);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'start',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'neverFails',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    });
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchVariables().withSuccess([]);

    init('process-instance', []);
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

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
      }),
    );

    render(<Variables />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await waitFor(() =>
      expect(
        flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate,
      ).toEqual(null),
    );

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled(),
    );

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() =>
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'neverFails',
        flowNodeInstanceId: '3',
        isMultiInstance: false,
      }),
    );

    await waitFor(() =>
      expect(
        flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate,
      ).toEqual(MOCK_TIMESTAMP),
    );

    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();

    flowNodeMetaDataStore.reset();
  });
  it('should disable add variable button when loading', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
    await waitFor(() =>
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled(),
    );
  });

  it('should disable add variable button if instance state is cancelled', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'TERMINATED',
    });
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

    render(<Variables />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled(),
    );
  });

  it('should hide/disable add variable button if add/edit variable button is clicked', async () => {
    jest.useFakeTimers();
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockProcessInstanceDeprecated().withSuccess(instanceMock);
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    await waitFor(() =>
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument(),
    );

    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));
    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    const [firstEditVariableButton] = screen.getAllByRole('button', {
      name: /edit variable/i,
    });
    expect(firstEditVariableButton).toBeInTheDocument();
    await user.click(firstEditVariableButton!);
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();

    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));
    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
