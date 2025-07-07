/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter} from 'react-router-dom';
import {render, screen, type UserEvent, waitFor} from 'modules/testing-library';
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
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockProcessXML} from 'modules/testUtils';

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
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
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
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
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

describe('Variables', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: 'process-instance-id'}),
    );
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'some-scope',
      flowNodeInstanceId: 'flow-node-instance-id',
    });
  });

  it('should not apply modification if modification mode is not enabled', async () => {
    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await editValueFromTextfieldAndBlur(user, '4');

    expect(modificationsStore.state.modifications.length).toBe(0);
  });

  it('should create edit variable modification on blur', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await editValueFromTextfieldAndBlur(user, '4');

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

    await editValueFromTextfieldAndBlur(user, '5');

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

    await editValueFromTextfieldAndBlur(user, '{backspace}{backspace}');

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
  });

  it('should not apply modification if value is invalid', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await editValueFromTextfieldAndBlur(user, 'invalid value');

    expect(modificationsStore.state.modifications).toEqual([]);
  });

  it('should not apply modification if value is empty', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="1" />,
      {wrapper: Wrapper},
    );

    await editValueFromTextfieldAndBlur(user, '{backspace}');

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

  it('should not apply modification if value is the same as the last modification', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await editValueFromTextfieldAndBlur(user, '4');

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

  it('should apply modification if its different from last modification but same as the initial value', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(
      <ExistingVariableValue variableName="foo" variableValue="123" />,
      {wrapper: Wrapper},
    );

    await editValueFromTextfieldAndBlur(user, '4');

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

    await editValueFromTextfieldAndBlur(user, '{backspace}');
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
  });
});
