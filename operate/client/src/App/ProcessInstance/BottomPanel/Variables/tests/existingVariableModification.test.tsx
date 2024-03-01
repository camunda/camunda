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

import {MemoryRouter} from 'react-router-dom';
import {render, screen, UserEvent, waitFor} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {ExistingVariableValue} from '../ExistingVariableValue';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';
import {modificationsStore} from 'modules/stores/modifications';
import {LastModification} from 'App/ProcessInstance/LastModification';
import {OnLastVariableModificationRemoved} from '../OnLastVariableModificationRemoved';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {createInstance} from 'modules/testUtils';
import {useEffect} from 'react';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  useEffect(() => {
    return () => {
      processInstanceDetailsStore.reset();
      variablesStore.reset();
      modificationsStore.reset();
      flowNodeSelectionStore.reset();
    };
  }, []);

  return (
    <MemoryRouter>
      <Form onSubmit={() => {}} mutators={{...arrayMutators}}>
        {({handleSubmit}) => {
          return (
            <>
              <form onSubmit={handleSubmit}>{children} </form>
              <OnLastVariableModificationRemoved />
              <LastModification />
            </>
          );
        }}
      </Form>
    </MemoryRouter>
  );
};

const editValueFromTextfieldAndBlur = async (
  user: UserEvent,
  value: string,
) => {
  await user.click(screen.getByTestId('edit-variable-value'));
  await user.type(screen.getByTestId('edit-variable-value'), value);
  await user.tab();
};

const editValueFromJSONEditor = async (user: UserEvent, value: string) => {
  await user.click(
    screen.getByRole('button', {name: /open json editor modal/i}),
  );
  await user.click(screen.getByTestId('monaco-editor'));
  await waitFor(() =>
    expect(screen.getByTestId('monaco-editor')).toHaveValue(),
  );
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

describe('Variables', () => {
  beforeEach(() => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: 'process-instance-id'}),
    );
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'some-scope',
      flowNodeInstanceId: 'flow-node-instance-id',
    });
  });

  it.each(['textfield', 'jsoneditor'])(
    'should not apply modification if modification mode is not enabled - %p',
    async (type) => {
      const {user} = render(
        <ExistingVariableValue variableName="foo" variableValue="123" />,
        {wrapper: Wrapper},
      );

      await editValue(type, user, '4');

      expect(modificationsStore.state.modifications.length).toBe(0);
    },
  );

  it.each(['textfield', 'jsoneditor'])(
    'should create edit variable modification on blur - %p',
    async (type) => {
      modificationsStore.enableModificationMode();

      const {user} = render(
        <ExistingVariableValue variableName="foo" variableValue="123" />,
        {wrapper: Wrapper},
      );

      await editValue(type, user, '4');

      expect(modificationsStore.state.modifications.length).toBe(1);
      expect(modificationsStore.variableModifications).toEqual([
        {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '1234',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      ]);

      await editValue(type, user, '5');

      expect(modificationsStore.state.modifications.length).toBe(2);
      expect(modificationsStore.variableModifications).toEqual([
        {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '12345',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      ]);

      await editValue(type, user, '{backspace}{backspace}');

      expect(modificationsStore.state.modifications.length).toBe(3);
      expect(modificationsStore.variableModifications).toEqual([
        {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '123',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      ]);

      await user.click(screen.getByRole('button', {name: 'Undo'}));

      expect(modificationsStore.state.modifications.length).toBe(2);
      expect(modificationsStore.variableModifications).toEqual([
        {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '12345',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      ]);
    },
  );

  it('should not apply modification if value is invalid', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await editValue('textfield', user, 'invalid value');

    expect(modificationsStore.state.modifications).toEqual([]);
  });

  it('should not apply modification if value is empty', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="1" />,
      {wrapper: Wrapper},
    );

    await editValue('textfield', user, '{backspace}');

    expect(modificationsStore.state.modifications).toEqual([]);
  });

  it('should not apply modification if value has not changed from textfield', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await user.click(screen.getByTestId('edit-variable-value'));
    await user.tab();

    expect(modificationsStore.state.modifications).toEqual([]);
  });

  it('should not apply modification if value has not changed from json editor', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {name: /open json editor modal/i}),
    );
    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(modificationsStore.state.modifications).toEqual([]);
  });

  it('should not apply modification if value is the same as the last modification', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await editValue('textfield', user, '4');

    expect(modificationsStore.state.modifications).toEqual([
      {
        type: 'variable',
        payload: {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '1234',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      },
    ]);

    await user.click(screen.getByTestId('edit-variable-value'));
    await user.type(screen.getByTestId('edit-variable-value'), '5');
    await user.type(screen.getByTestId('edit-variable-value'), '{backspace}');

    await user.tab();

    expect(modificationsStore.state.modifications).toEqual([
      {
        type: 'variable',
        payload: {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '1234',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      },
    ]);
  });

  it.each(['textfield', 'jsoneditor'])(
    'should apply modification if its different from last modification but same as the initial value - %p',
    async (type) => {
      modificationsStore.enableModificationMode();

      const {user} = render(
        <ExistingVariableValue variableName="foo" variableValue="123" />,
        {wrapper: Wrapper},
      );

      await editValue(type, user, '4');

      expect(modificationsStore.state.modifications).toEqual([
        {
          type: 'variable',
          payload: {
            flowNodeName: 'some-scope',
            id: 'foo',
            name: 'foo',
            newValue: '1234',
            oldValue: '123',
            operation: 'EDIT_VARIABLE',
            scopeId: 'flow-node-instance-id',
          },
        },
      ]);
      expect(modificationsStore.variableModifications).toEqual([
        {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '1234',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      ]);

      await editValue(type, user, '{backspace}');
      expect(modificationsStore.state.modifications).toEqual([
        {
          type: 'variable',
          payload: {
            flowNodeName: 'some-scope',
            id: 'foo',
            name: 'foo',
            newValue: '1234',
            oldValue: '123',
            operation: 'EDIT_VARIABLE',
            scopeId: 'flow-node-instance-id',
          },
        },
        {
          type: 'variable',
          payload: {
            flowNodeName: 'some-scope',
            id: 'foo',
            name: 'foo',
            newValue: '123',
            oldValue: '123',
            operation: 'EDIT_VARIABLE',
            scopeId: 'flow-node-instance-id',
          },
        },
      ]);
      expect(modificationsStore.variableModifications).toEqual([
        {
          flowNodeName: 'some-scope',
          id: 'foo',
          name: 'foo',
          newValue: '123',
          oldValue: '123',
          operation: 'EDIT_VARIABLE',
          scopeId: 'flow-node-instance-id',
        },
      ]);
    },
  );
});
