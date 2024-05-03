/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
