/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {
  render,
  screen,
  fireEvent,
  type UserEvent,
  waitFor,
} from 'modules/testing-library';

import {LastModification} from 'App/ProcessInstance/LastModification';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {createVariable} from 'modules/testUtils';
import {
  modificationsStore,
  type FlowNodeModification,
} from 'modules/stores/modifications';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

/**
 * Variables can only be added and modified in the root if a pending add/move token modification exists.
 * Tests need to queue this modification first, before they can make changes to the root variables.
 */
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

const editLastNewVariableName = async (
  user: UserEvent,
  value: string,
  index: number = 0,
) => {
  const nameField = screen.getAllByTestId('new-variable-name').at(-1);

  if (!nameField) {
    throw new Error(`No name field found at index ${index}`);
  }

  await user.click(nameField);
  fireEvent.focus(nameField);
  await user.type(nameField, value);
  await user.tab();
};

const editLastNewVariableValue = async (
  user: UserEvent,
  value: string,
  index: number = 0,
) => {
  const valueField = screen.getAllByTestId('new-variable-value').at(-1);

  if (!valueField) {
    throw new Error(`No value field found at index ${index}`);
  }

  await user.click(valueField);
  await user.type(valueField, value);
  await user.tab();
};

const TestSelectionControls: React.FC = () => {
  const {selectElementInstance, selectElement, clearSelection} =
    useProcessInstanceElementSelection();
  return (
    <>
      <button
        type="button"
        onClick={() =>
          selectElementInstance({
            elementId: 'TEST_FLOW_NODE',
            elementInstanceKey: 'instance_id',
          })
        }
      >
        select flow node instance
      </button>
      <button
        type="button"
        onClick={() =>
          selectElement({
            elementId: 'flow-node-that-has-not-run-yet',
          })
        }
      >
        select flow node from diagram
      </button>
      <button type="button" onClick={() => clearSelection()}>
        clear selection
      </button>
    </>
  );
};

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('instance_id')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        modificationsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route
                path={Paths.processInstance()}
                element={
                  <>
                    <TestSelectionControls />
                    {children}
                  </>
                }
              />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe('New Variable Modifications', () => {
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
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should not create add variable modification if fields are empty', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    vi.useFakeTimers({shouldAdvanceTime: true});
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    vi.runOnlyPendingTimers();
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();
    await user.click(screen.getByTestId('new-variable-name'));
    await user.tab();
    await user.click(screen.getByTestId('new-variable-value'));
    await user.tab();
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    expect(modificationsStore.state.modifications.length).toBe(1);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not create add variable modification if name field is empty', async () => {
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    vi.useFakeTimers({shouldAdvanceTime: true});

    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();
    await user.click(screen.getByTestId('new-variable-name'));
    await user.tab();
    await editLastNewVariableValue(user, '123');
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    expect(
      await screen.findByText(/Name has to be filled/i),
    ).toBeInTheDocument();
    expect(modificationsStore.state.modifications.length).toBe(1);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not create add variable modification if name field is duplicate', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
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
    await editLastNewVariableName(user, 'testVariableName');
    await editLastNewVariableValue(user, '123');
    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled(),
    );
    expect(
      await screen.findByText(/Name should be unique/i),
    ).toBeInTheDocument();

    await user.clear(screen.getByTestId('new-variable-name'));
    await editLastNewVariableName(user, 'test2');

    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '123',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    await editLastNewVariableName(user, 'test2');
    await editLastNewVariableValue(user, '1234');
    expect(
      await screen.findByText(/Name should be unique/i),
    ).toBeInTheDocument();
    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '123',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not create add variable modification if value field is empty or invalid', async () => {
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    vi.useFakeTimers({shouldAdvanceTime: true});
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

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
    await user.click(screen.getByTestId('new-variable-value'));
    await user.tab();
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    expect(
      await screen.findByText(/Value has to be filled/i),
    ).toBeInTheDocument();
    expect(modificationsStore.state.modifications.length).toBe(1);
    await editLastNewVariableValue(user, 'invalid value');
    expect(
      await screen.findByText(/Value has to be JSON/i),
    ).toBeInTheDocument();
    expect(modificationsStore.state.modifications.length).toBe(1);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should create add variable modification on blur and update same modification if name or value is changed', async () => {
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    vi.useFakeTimers({shouldAdvanceTime: true});

    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    const {user} = render(
      <>
        <VariablePanel setListenerTabVisibility={vi.fn()} />
        <LastModification />
      </>,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await editLastNewVariableName(user, 'test2');
    await editLastNewVariableValue(user, '12345');

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    await user.click(screen.getByTestId('new-variable-name'));
    await user.tab();
    await user.click(screen.getByTestId('new-variable-value'));
    await user.tab();

    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    await editLastNewVariableName(user, '-updated');

    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2-updated',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    expect(
      modificationsStore.getAddVariableModifications('instance_id'),
    ).toEqual([
      {
        id: expect.any(String),
        name: 'test2-updated',
        value: '12345',
      },
    ]);

    await editLastNewVariableValue(user, '678');
    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2-updated',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2-updated',
          newValue: '12345678',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    expect(
      modificationsStore.getAddVariableModifications('instance_id'),
    ).toEqual([
      {
        id: expect.any(String),
        name: 'test2-updated',
        value: '12345678',
      },
    ]);

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2-updated',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    expect(
      modificationsStore.getAddVariableModifications('instance_id'),
    ).toEqual([
      {
        id: expect.any(String),
        name: 'test2-updated',
        value: '12345',
      },
    ]);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not apply modification if value is the same as the last modification', async () => {
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    vi.useFakeTimers({shouldAdvanceTime: true});
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await user.click(screen.getByTestId('new-variable-name'));
    await user.tab();

    await user.click(screen.getByTestId('new-variable-value'));
    await user.tab();

    await user.clear(screen.getByTestId('new-variable-name'));
    await editLastNewVariableName(user, 'test2');
    await user.clear(screen.getByTestId('new-variable-value'));
    await editLastNewVariableValue(user, '12345');

    expect(modificationsStore.state.modifications).toEqual([
      INITIAL_ADD_MODIFICATION,
      {
        payload: {
          flowNodeName: 'someProcessName',
          id: expect.any(String),
          name: 'test2',
          newValue: '12345',
          operation: 'ADD_VARIABLE',
          scopeId: 'instance_id',
        },
        type: 'variable',
      },
    ]);

    expect(
      modificationsStore.getAddVariableModifications('instance_id'),
    ).toEqual([
      {
        id: expect.any(String),
        name: 'test2',
        value: '12345',
      },
    ]);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should be able to remove the first added variable modification after switching between flow node instances', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    modificationsStore.enableModificationMode();
    modificationsStore.addModification(INITIAL_ADD_MODIFICATION);

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    // add first variable
    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await editLastNewVariableName(user, 'test1');
    await editLastNewVariableValue(user, '123');

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    // add second variable
    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findAllByTestId('new-variable-name')).toHaveLength(2);

    await editLastNewVariableName(user, 'test2', 1);
    await editLastNewVariableValue(user, '456', 1);

    mockFetchElementInstance('instance_id').withSuccess({
      elementInstanceKey: 'instance_id',
      elementId: 'TEST_FLOW_NODE',
      elementName: 'Test Flow Node',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processInstanceKey: 'instance_id',
      processDefinitionKey: '2',
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      hasIncident: false,
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(
      screen.getByRole('button', {name: /select flow node instance/i}),
    );

    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(screen.getByRole('button', {name: /clear selection/i}));

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    const [deleteFirstAddedVariable] = screen.getAllByRole('button', {
      name: /delete variable/i,
    });
    await user.click(deleteFirstAddedVariable!);

    expect(screen.queryByDisplayValue('test1')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('123')).not.toBeInTheDocument();

    expect(screen.getByDisplayValue('test2')).toBeInTheDocument();
    expect(screen.getByDisplayValue('456')).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should be able to add variable when a flow node that has no tokens on it is selected from the diagram', async () => {
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    vi.useFakeTimers({shouldAdvanceTime: true});

    modificationsStore.enableModificationMode();
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {
          id: 'flow-node-that-has-not-run-yet',
          name: 'some-flow-node',
        },
        scopeId: 'some-scope-id',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    mockSearchElementInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {
        wrapper: getWrapper([
          `${Paths.processInstance('instance_id')}?elementId=flow-node-that-has-not-run-yet`,
        ]),
      },
    );
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await editLastNewVariableName(user, 'test1');
    await editLastNewVariableValue(user, '123');

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchElementInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    await user.click(
      screen.getByRole('button', {name: /select flow node from diagram/i}),
    );

    expect(await screen.findByDisplayValue('test1')).toBeInTheDocument();
    expect(screen.getByDisplayValue('123')).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
