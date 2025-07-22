/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {mockVariables, mockVariablesV2} from '../index.setup';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  createInstance,
  createVariable,
  createVariableV2,
} from 'modules/testUtils';
import {getWrapper, mockProcessInstance} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {VariablePanel} from '../../VariablePanel';

const instanceMock = createInstance({id: '1'});

vi.mock('modules/feature-flags', async () => {
  const actual = await vi.importActual('modules/feature-flags');
  return {
    ...actual,
    IS_PROCESS_INSTANCE_V2_ENABLED: true,
  };
});

describe('Variables', () => {
  beforeEach(() => {
    flowNodeSelectionStore.init();

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockProcessInstanceDeprecated().withSuccess(instanceMock);
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should render variables table', async () => {
    mockSearchVariables().withSuccess(mockVariablesV2);
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess(mockVariables);

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

    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Value')).toBeInTheDocument();
    const {items} = mockVariablesV2;

    items.forEach((item) => {
      const withinVariableRow = within(
        screen.getByTestId(`variable-${item.name}`),
      );

      expect(withinVariableRow.getByText(item.name)).toBeInTheDocument();
      expect(withinVariableRow.getByText(item.value)).toBeInTheDocument();
    });
  });

  it.skip('should show/hide spinner next to variable according to it having an active operation', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess(mockVariables);

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
    const {items} = variablesStore.state;
    const [activeOperationVariable] = items.filter(
      ({hasActiveOperation}) => hasActiveOperation,
    );

    expect(activeOperationVariable).toBeDefined();
    expect(
      within(
        screen.getByTestId(`variable-${activeOperationVariable!.name}`),
      ).getByTestId('variable-operation-spinner'),
    ).toBeInTheDocument();

    const [inactiveOperationVariable] = items.filter(
      ({hasActiveOperation}) => !hasActiveOperation,
    );

    expect(
      within(
        screen.getByTestId(`variable-${inactiveOperationVariable!.name!}`),
      ).queryByTestId('variable-operation-spinner'),
    ).not.toBeInTheDocument();
  });

  it('should have a button to see full variable value', async () => {
    mockSearchVariables().withSuccess({
      items: [createVariableV2({isTruncated: true})],
      page: {
        totalItems: 1,
      },
    });
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'COMPLETED',
    });
    processInstanceDetailsStore.setProcessInstance({
      ...instanceMock,
      state: 'COMPLETED',
    });

    mockFetchVariables().withSuccess([createVariable({isPreview: true})]);

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
