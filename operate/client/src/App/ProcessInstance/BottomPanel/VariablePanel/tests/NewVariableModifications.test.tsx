/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {useEffect} from 'react';
import {act} from 'react-dom/test-utils';
import {Paths} from 'modules/Routes';

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

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      variablesStore.reset();
      flowNodeSelectionStore.reset();
      flowNodeMetaDataStore.reset();
      processInstanceDetailsDiagramStore.reset();
      modificationsStore.reset();
      processInstanceDetailsStatisticsStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      <Routes>
        <Route path={Paths.processInstance()} element={children} />
      </Routes>
    </MemoryRouter>
  );
};

describe('New Variable Modifications', () => {
  beforeEach(() => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ]);

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
    processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'instance_id',
    );
  });

  it('should not create add variable modification if fields are empty', async () => {
    jest.useFakeTimers();
    modificationsStore.enableModificationMode();

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
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

      const {user} = render(<VariablePanel />, {wrapper: Wrapper});
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

      const {user} = render(<VariablePanel />, {wrapper: Wrapper});
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

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
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
        {wrapper: Wrapper},
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

      const {user} = render(<VariablePanel />, {wrapper: Wrapper});
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

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
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

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    await waitFor(() =>
      expect(processInstanceDetailsStatisticsStore.state.status).toBe(
        'fetched',
      ),
    );
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
