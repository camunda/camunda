/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor, type UserEvent} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {createInstance, createvariable} from 'modules/testUtils';
import {
  modificationsStore,
  type FlowNodeModification,
} from 'modules/stores/modifications';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {useEffect, act} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

const INITIAL_ADD_MODIFICATION: FlowNodeModification = {
  type: 'token',
  payload: {
    affectedTokenCount: 1,
    flowNode: {
      id: 'flow_node_0',
      name: 'flow node 0',
    },
    operation: 'ADD_TOKEN',
    parentScopeIds: {},
    scopeId: 'random-scope-id-0',
    visibleAffectedTokenCount: 1,
  },
};

const editExistingVariableValue = async (
  user: UserEvent,
  variableName: string,
  newValue: string,
) => {
  const variableRow = screen.getByTestId(`variable-${variableName}`);
  const valueField = variableRow.querySelector('input');

  if (!valueField) {
    throw new Error(`No value field found for variable ${variableName}`);
  }

  await user.click(valueField);
  await user.keyboard('{Control>}a{/Control}');
  await user.keyboard('{Backspace}');
  await user.type(valueField, newValue);
  await user.tab();
};

const editLastNewVariableName = async (user: UserEvent, value: string) => {
  const nameField = screen.getAllByTestId('new-variable-name').at(-1);

  if (!nameField) {
    throw new Error('No name field found');
  }

  await user.click(nameField);
  await user.type(nameField, value);
  await user.tab();
};

const editLastNewVariableValue = async (user: UserEvent, value: string) => {
  const valueField = screen.getAllByTestId('new-variable-value').at(-1);

  if (!valueField) {
    throw new Error('No value field found');
  }

  await user.click(valueField);
  await user.type(valueField, value);
  await user.tab();
};

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        flowNodeSelectionStore.reset();
        flowNodeMetaDataStore.reset();
        modificationsStore.reset();
        processInstanceDetailsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe('Undo variable modifications from different scope', () => {
  const mockProcessInstance: ProcessInstance = {
    processInstanceKey: 'instance_id',
    state: 'ACTIVE',
    startDate: '2018-06-21',
    processDefinitionKey: '2',
    processDefinitionVersion: 1,
    processDefinitionId: 'someKey',
    tenantId: '<default>',
    processDefinitionName: 'someProcessName',
    hasIncident: false,
  };
  const mockProcessInstanceDeprecated = createInstance({id: 'instance_id'});

  beforeEach(async () => {
    const statisticsData = [
      {
        elementId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statisticsData,
    });
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');

    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
  });

  it('should preserve earlier edit modifications after undoing from a different scope', async () => {
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    mockSearchVariables().withSuccess({
      items: [
        createvariable({name: 'foo', value: '"bar"', isTruncated: false}),
        createvariable({name: 'test', value: '123', isTruncated: false}),
      ],
      page: {totalItems: 2},
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByDisplayValue('"bar"')).toBeInTheDocument();
    expect(screen.getByDisplayValue('123')).toBeInTheDocument();

    await editExistingVariableValue(user, 'foo', '1');
    await waitFor(() => {
      expect(screen.getByDisplayValue('1')).toBeInTheDocument();
    });

    await editExistingVariableValue(user, 'test', '2');
    expect(screen.getByDisplayValue('2')).toBeInTheDocument();

    await editExistingVariableValue(user, 'foo', '3');
    expect(screen.getByDisplayValue('3')).toBeInTheDocument();

    act(() => {
      modificationsStore.removeLastModification();
    });
    expect(screen.getByDisplayValue('1')).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockSearchVariables().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    act(() => {
      selectFlowNode(
        {flowNodeInstanceId: 'different_instance_id', isMultiInstance: false},
        {
          flowNodeInstanceId: 'different_instance_id',
          isMultiInstance: false,
        },
      );
    });

    act(() => {
      modificationsStore.removeLastModification();
    });

    mockSearchVariables().withSuccess({
      items: [
        createvariable({name: 'foo', value: '"bar"', isTruncated: false}),
        createvariable({name: 'test', value: '123', isTruncated: false}),
      ],
      page: {totalItems: 2},
    });

    act(() => {
      selectFlowNode(
        {flowNodeInstanceId: 'instance_id', isMultiInstance: false},
        {
          flowNodeInstanceId: 'instance_id',
          isMultiInstance: false,
        },
      );
    });

    await waitFor(() => {
      expect(screen.getByDisplayValue('1')).toBeInTheDocument();
      expect(screen.getByDisplayValue('123')).toBeInTheDocument();
    });
  });

  it('should preserve earlier added variable modifications after undoing from a different scope', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    mockSearchVariables().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();
    await editLastNewVariableName(user, 'test2');
    await editLastNewVariableValue(user, '1');

    expect(screen.getByDisplayValue('test2')).toBeInTheDocument();
    expect(screen.getByDisplayValue('1')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    await waitFor(() => {
      expect(screen.getAllByTestId('new-variable-name')).toHaveLength(2);
    });
    await editLastNewVariableName(user, 'test3');
    await editLastNewVariableValue(user, '2');

    expect(screen.getByDisplayValue('test3')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2')).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockSearchVariables().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    act(() => {
      selectFlowNode(
        {flowNodeInstanceId: 'different_instance_id', isMultiInstance: false},
        {
          flowNodeInstanceId: 'different_instance_id',
          isMultiInstance: false,
        },
      );
    });

    act(() => {
      modificationsStore.removeLastModification();
    });

    mockSearchVariables().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    act(() => {
      selectFlowNode(
        {flowNodeInstanceId: 'instance_id', isMultiInstance: false},
        {
          flowNodeInstanceId: 'instance_id',
          isMultiInstance: false,
        },
      );
    });

    await waitFor(() => {
      expect(screen.getByDisplayValue('test2')).toBeInTheDocument();
      expect(screen.getByDisplayValue('1')).toBeInTheDocument();
    });

    expect(screen.queryByDisplayValue('test3')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('2')).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
