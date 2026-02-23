/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor, type UserEvent} from 'modules/testing-library';
import {createVariable} from 'modules/testUtils';
import {
  modificationsStore,
  type FlowNodeModification,
} from 'modules/stores/modifications';
import {act} from 'react';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {getWrapper as getBaseWrapper, mockProcessInstance} from './mocks';

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

const TestSelectionControls: React.FC = () => {
  const {selectElementInstance, clearSelection} =
    useProcessInstanceElementSelection();
  return (
    <>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'different_flow_node',
            elementInstanceKey: 'different_instance_id',
          })
        }
      >
        select different scope
      </button>
      <button type="button" onClick={() => clearSelection()}>
        clear selection
      </button>
    </>
  );
};

const getWrapper = (...args: Parameters<typeof getBaseWrapper>) => {
  const BaseWrapper = getBaseWrapper(...args);

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <BaseWrapper>
      <>
        <TestSelectionControls />
        {children}
      </>
    </BaseWrapper>
  );

  return Wrapper;
};

describe('Undo variable modifications from different scope', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should preserve earlier edit modifications after undoing from a different scope', async () => {
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    mockSearchVariables().withSuccess({
      items: [
        createVariable({name: 'foo', value: '"bar"', isTruncated: false}),
        createVariable({name: 'test', value: '123', isTruncated: false}),
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

    mockSearchVariables().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockFetchElementInstance('different_instance_id').withSuccess({
      elementInstanceKey: 'different_instance_id',
      elementId: 'different_flow_node',
      elementName: 'Different Flow Node',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionId: 'someKey',
      processInstanceKey: mockProcessInstance.processInstanceKey,
      processDefinitionKey: '2',
      hasIncident: false,
      tenantId: '<default>',
    });

    await user.click(
      screen.getByRole('button', {name: /select different scope/i}),
    );

    act(() => {
      modificationsStore.removeLastModification();
    });

    mockSearchVariables().withSuccess({
      items: [
        createVariable({name: 'foo', value: '"bar"', isTruncated: false}),
        createVariable({name: 'test', value: '123', isTruncated: false}),
      ],
      page: {totalItems: 2},
    });

    await user.click(screen.getByRole('button', {name: /clear selection/i}));

    expect(screen.getByDisplayValue('1')).toBeInTheDocument();
    expect(screen.getByDisplayValue('123')).toBeInTheDocument();
  });

  it('should preserve earlier added variable modifications after undoing from a different scope', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

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

    mockSearchVariables().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockFetchElementInstance('different_instance_id').withSuccess({
      elementInstanceKey: 'different_instance_id',
      elementId: 'different_flow_node',
      elementName: 'Different Flow Node',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionId: 'someKey',
      processInstanceKey: mockProcessInstance.processInstanceKey,
      processDefinitionKey: '2',
      hasIncident: false,
      tenantId: '<default>',
    });

    await user.click(
      screen.getByRole('button', {name: /select different scope/i}),
    );

    act(() => {
      modificationsStore.removeLastModification();
    });

    mockSearchVariables().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    await user.click(screen.getByRole('button', {name: /clear selection/i}));

    expect(screen.getByDisplayValue('test2')).toBeInTheDocument();
    expect(screen.getByDisplayValue('1')).toBeInTheDocument();

    expect(screen.queryByDisplayValue('test3')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('2')).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
