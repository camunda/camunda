/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {getWrapper, mockProcessInstance} from './mocks';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {
  createInstance,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {act} from 'react';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init} from 'modules/utils/flowNodeMetadata';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockVariables} from './index.setup';
import {VariablePanel} from '../index';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';

const instanceMock = createInstance({id: '1'});

describe('Footer', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
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
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    init('process-instance', [
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
    ]);
  });

  afterEach(() => {
    act(() => {
      flowNodeSelectionStore.reset();
      flowNodeMetaDataStore.reset();
      processInstanceDetailsStore.reset();
    });
  });

  it('should hide/disable add variable button if add/edit variable button is clicked', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

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
  });

  // TODO: fix test with #44450
  it.skip('should disable add variable button when selected flow node is not running', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    const mockSearchVariablesPayload = {
      items: [],
      page: {
        totalItems: 0,
      },
    };
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchVariables().withSuccess(mockSearchVariablesPayload);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    const mockFetchFlowNodeMetadataPayload = {
      ...singleInstanceMetadata,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    };
    mockFetchFlowNodeMetadata().withSuccess(mockFetchFlowNodeMetadataPayload);
    mockFetchFlowNodeMetadata().withSuccess(mockFetchFlowNodeMetadataPayload);
    mockFetchFlowNodeMetadata().withSuccess(mockFetchFlowNodeMetadataPayload);

    init('process-instance', []);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'start',
      flowNodeInstanceId: '2',
      isMultiInstance: false,
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(
        flowNodeMetaDataStore.state.metaData?.instanceMetadata?.endDate,
      ).toEqual(null),
    );

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled(),
    );

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
  });

  it('should disable add variable button when loading', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled(),
    );
  });

  it('should disable add variable button if instance state is cancelled', async () => {
    processInstanceDetailsStore.setProcessInstance({
      ...instanceMock,
      state: 'CANCELED',
    });
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'TERMINATED',
    });
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled(),
    );
  });
});
