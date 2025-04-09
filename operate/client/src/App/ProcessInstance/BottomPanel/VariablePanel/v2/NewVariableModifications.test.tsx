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
  UserEvent,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';

import {LastModification} from 'App/ProcessInstance/LastModification';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {createInstance, createVariable} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {useEffect, act} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';

jest.mock('modules/feature-flags', () => ({
  ...jest.requireActual('modules/feature-flags'),
  IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED: true,
}));

const editNameFromTextfieldAndBlur = async (user: UserEvent, value: string) => {
  const [nameField] = screen.getAllByTestId('new-variable-name');

  await user.click(nameField!);
  await user.type(nameField!, value);
  await user.tab();
};

const editValueFromTextfieldAndBlur = async (
  user: UserEvent,
  value: string,
) => {
  const [valueField] = screen.getAllByTestId('new-variable-value');

  await user.click(valueField!);
  await user.type(valueField!, value);
  await user.tab();
};

const editValueFromJSONEditor = async (user: UserEvent, value: string) => {
  const [jsonEditor] = screen.getAllByRole('button', {
    name: /open json editor modal/i,
  });
  await user.click(jsonEditor!);
  await user.click(await screen.findByTestId('monaco-editor'));
  await user.type(screen.getByTestId('monaco-editor'), value);
  await user.click(screen.getByRole('button', {name: /apply/i}));
};

const editValue = async (type: string, user: UserEvent, value: string) => {
  if (type === 'textfield') {
    return editValueFromTextfieldAndBlur(user, value);
  }
  if (type === 'jsoneditor') {
    return editValueFromJSONEditor(user, value);
  }
};

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        variablesStore.reset();
        flowNodeSelectionStore.reset();
        flowNodeMetaDataStore.reset();
        modificationsStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
  return Wrapper;
};

describe('New Variable Modifications', () => {
  beforeEach(async () => {
    const statisticsData = [
      {
        flowNodeId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        flowNodeId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statisticsData,
    });
    mockFetchVariables().withSuccess([createVariable()]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
  });

  it('should not create add variable modification if fields are empty', async () => {
    jest.useFakeTimers();
    modificationsStore.enableModificationMode();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(() =>
      screen.getByTestId('variables-skeleton'),
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
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    expect(modificationsStore.state.modifications.length).toBe(0);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it.each(['textfield', 'jsoneditor'])(
    'should not create add variable modification if name field is empty - %p',
    async (type) => {
      jest.useFakeTimers();

      modificationsStore.enableModificationMode();

      const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i}),
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name'),
      ).toBeInTheDocument();
      await user.click(screen.getByTestId('new-variable-name'));
      await user.tab();
      await editValue(type, user, '123');
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled();
      expect(
        await screen.findByText(/Name has to be filled/i),
      ).toBeInTheDocument();
      expect(modificationsStore.state.modifications.length).toBe(0);

      jest.clearAllTimers();
      jest.useRealTimers();
    },
  );

  it.each(['textfield', 'jsoneditor'])(
    'should not create add variable modification if name field is duplicate - %p',
    async (type) => {
      jest.useFakeTimers();
      modificationsStore.enableModificationMode();

      const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i}),
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name'),
      ).toBeInTheDocument();
      await editNameFromTextfieldAndBlur(user, 'testVariableName');
      await editValue(type, user, '123');
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeDisabled();
      expect(
        await screen.findByText(/Name should be unique/i),
      ).toBeInTheDocument();
      expect(modificationsStore.state.modifications.length).toBe(0);

      await user.clear(screen.getByTestId('new-variable-name'));
      await editNameFromTextfieldAndBlur(user, 'test2');

      expect(modificationsStore.state.modifications).toEqual([
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
      await editNameFromTextfieldAndBlur(user, 'test2');
      await editValue(type, user, '1234');
      expect(
        await screen.findByText(/Name should be unique/i),
      ).toBeInTheDocument();
      expect(modificationsStore.state.modifications).toEqual([
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

      jest.clearAllTimers();
      jest.useRealTimers();
    },
  );

  it('should not create add variable modification if value field is empty or invalid', async () => {
    jest.useFakeTimers();
    modificationsStore.enableModificationMode();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await editNameFromTextfieldAndBlur(user, 'test2');
    await user.click(screen.getByTestId('new-variable-value'));
    await user.tab();
    expect(screen.getByRole('button', {name: /add variable/i})).toBeDisabled();
    expect(
      await screen.findByText(/Value has to be filled/i),
    ).toBeInTheDocument();
    expect(modificationsStore.state.modifications.length).toBe(0);
    await editValueFromTextfieldAndBlur(user, 'invalid value');
    expect(
      await screen.findByText(/Value has to be JSON/i),
    ).toBeInTheDocument();
    expect(modificationsStore.state.modifications.length).toBe(0);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it.each(['textfield', 'jsoneditor'])(
    'should create add variable modification on blur and update same modification if name or value is changed - %p',
    async (type) => {
      jest.useFakeTimers();

      modificationsStore.enableModificationMode();

      const {user} = render(
        <>
          <VariablePanel />
          <LastModification />
        </>,
        {wrapper: getWrapper()},
      );
      await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i}),
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name'),
      ).toBeInTheDocument();

      await editNameFromTextfieldAndBlur(user, 'test2');
      await editValue(type, user, '12345');

      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
      expect(modificationsStore.state.modifications).toEqual([
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

      await editNameFromTextfieldAndBlur(user, '-updated');

      expect(modificationsStore.state.modifications).toEqual([
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

      await editValue(type, user, '678');
      expect(modificationsStore.state.modifications).toEqual([
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

      jest.clearAllTimers();
      jest.useRealTimers();
    },
  );

  it.each(['textfield', 'jsoneditor'])(
    'should not apply modification if value is the same as the last modification - %p',
    async (type) => {
      jest.useFakeTimers();
      modificationsStore.enableModificationMode();

      const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

      await waitFor(() => {
        expect(
          screen.getByRole('button', {name: /add variable/i}),
        ).toBeEnabled();
      });

      await user.click(screen.getByRole('button', {name: /add variable/i}));
      expect(
        await screen.findByTestId('new-variable-name'),
      ).toBeInTheDocument();

      await user.click(screen.getByTestId('new-variable-name'));
      await user.tab();

      await user.click(screen.getByTestId('new-variable-value'));
      await user.tab();

      await user.clear(screen.getByTestId('new-variable-name'));
      await editNameFromTextfieldAndBlur(user, 'test2');
      await user.clear(screen.getByTestId('new-variable-value'));
      await editValue(type, user, '12345');

      expect(modificationsStore.state.modifications).toEqual([
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

      jest.clearAllTimers();
      jest.useRealTimers();
    },
  );

  it('should be able to remove the first added variable modification after switching between flow node instances', async () => {
    jest.useFakeTimers();
    modificationsStore.enableModificationMode();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    // add first variable
    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await editNameFromTextfieldAndBlur(user, 'test1');
    await editValueFromTextfieldAndBlur(user, '123');

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    // add second variable
    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findAllByTestId('new-variable-name')).toHaveLength(2);

    await editNameFromTextfieldAndBlur(user, 'test2');
    await editValueFromTextfieldAndBlur(user, '456');

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    mockFetchVariables().withSuccess([]);

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'someProcessName',
        flowNodeInstanceId: 'test',
      });
    });

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    mockFetchVariables().withSuccess([]);

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeInstanceId: 'instance_id',
        isMultiInstance: false,
      });
    });

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    const [, deleteFirstAddedVariable] = screen.getAllByRole('button', {
      name: /delete variable/i,
    });
    await user.click(deleteFirstAddedVariable!);

    expect(screen.queryByDisplayValue('test1')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('123')).not.toBeInTheDocument();

    expect(screen.getByDisplayValue('test2')).toBeInTheDocument();
    expect(screen.getByDisplayValue('456')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should be able to add variable when a flow node that has no tokens on it is selected from the diagram', async () => {
    jest.useFakeTimers();

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

    // select flow node without instance id (use case: from the diagram)
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'flow-node-that-has-not-run-yet',
    });

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(await screen.findByTestId('new-variable-name')).toBeInTheDocument();

    await editNameFromTextfieldAndBlur(user, 'test1');
    await editValueFromTextfieldAndBlur(user, '123');

    expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();

    mockFetchVariables().withSuccess([]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'someProcessName',
        flowNodeInstanceId: 'test',
      });
    });

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    mockFetchVariables().withSuccess([]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'flow-node-that-has-not-run-yet',
      });
    });

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(await screen.findByDisplayValue('test1')).toBeInTheDocument();
    expect(screen.getByDisplayValue('123')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
