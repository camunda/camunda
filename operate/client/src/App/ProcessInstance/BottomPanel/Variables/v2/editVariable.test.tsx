/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  within,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import Variables from '../index';
import {Wrapper, mockVariables} from './mocks';
import {createInstance, createVariable} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchVariable} from 'modules/mocks/api/fetchVariable';
import {act} from 'react';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

const instanceMock = createInstance({id: '1'});

describe('Edit variable', () => {
  it('should show/hide edit button next to variable according to it having an active operation', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    const [activeOperationVariable] = variablesStore.state.items.filter(
      ({hasActiveOperation}) => hasActiveOperation,
    );

    expect(
      within(
        screen.getByTestId(`variable-${activeOperationVariable!.name}`),
      ).queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();

    const [inactiveOperationVariable] = variablesStore.state.items.filter(
      ({hasActiveOperation}) => !hasActiveOperation,
    );

    expect(inactiveOperationVariable).toBeDefined();
    expect(
      within(
        screen.getByTestId(`variable-${inactiveOperationVariable!.name}`),
      ).getByRole('button', {name: /edit variable/i}),
    ).toBeInTheDocument();
  });

  it('should not display edit button next to variables if instance is completed or canceled', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    const [inactiveOperationVariable] = variablesStore.state.items.filter(
      ({hasActiveOperation}) => !hasActiveOperation,
    );

    expect(inactiveOperationVariable).toBeDefined();
    expect(
      within(
        screen.getByTestId(`variable-${inactiveOperationVariable!.name}`),
      ).getByRole('button', {name: /edit variable/i}),
    ).toBeInTheDocument();

    act(() =>
      processInstanceDetailsStore.setProcessInstance({
        ...instanceMock,
        state: 'CANCELED',
      }),
    );

    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
  });

  it('should show/hide edit variable inputs', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.queryByTestId('add-variable-value')).not.toBeInTheDocument();

    const [firstVariable] = variablesStore.state.items;
    expect(firstVariable).toBeDefined();
    const withinFirstVariable = within(
      screen.getByTestId(`variable-${firstVariable!.name}`),
    );
    expect(
      withinFirstVariable.queryByTestId('edit-variable-value'),
    ).not.toBeInTheDocument();
    expect(
      withinFirstVariable.queryByRole('button', {name: /exit edit mode/i}),
    ).not.toBeInTheDocument();
    expect(
      withinFirstVariable.queryByRole('button', {name: /save variable/i}),
    ).not.toBeInTheDocument();

    await user.click(
      withinFirstVariable.getByRole('button', {name: /edit variable/i}),
    );

    expect(
      withinFirstVariable.getByTestId('edit-variable-value'),
    ).toBeInTheDocument();
    expect(
      withinFirstVariable.getByRole('button', {name: /exit edit mode/i}),
    ).toBeInTheDocument();
    expect(
      withinFirstVariable.getByRole('button', {name: /save variable/i}),
    ).toBeInTheDocument();
  });

  it('should disable save button when nothing is changed', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.queryByTestId('add-variable-value')).not.toBeInTheDocument();

    const [firstVariable] = variablesStore.state.items;
    expect(firstVariable).toBeDefined();
    const withinFirstVariable = within(
      screen.getByTestId(`variable-${firstVariable!.name}`),
    );

    await user.click(
      withinFirstVariable.getByRole('button', {name: /edit variable/i}),
    );

    expect(
      withinFirstVariable.getByRole('button', {name: /save variable/i}),
    ).toBeDisabled();
  });

  it('should validate when editing variables', async () => {
    jest.useFakeTimers();
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();

    const [firstVariable] = variablesStore.state.items;
    expect(firstVariable).toBeDefined();
    const withinFirstVariable = within(
      screen.getByTestId(`variable-${firstVariable!.name}`),
    );

    await user.click(
      withinFirstVariable.getByRole('button', {name: /edit variable/i}),
    );
    await user.type(
      screen.getByTestId('edit-variable-value'),
      "{{invalidKey: 'value'}}",
    );

    expect(screen.getByRole('button', {name: /save variable/i})).toBeDisabled();
    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    await user.clear(screen.getByTestId('edit-variable-value'));
    await user.type(screen.getByTestId('edit-variable-value'), '123');

    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /save variable/i}),
      ).toBeEnabled(),
    );

    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should get variable details on edit button click if the variables value was a preview', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess([
      createVariable({
        name: 'clientNo',
        value: '"value-preview"',
        isPreview: true,
      }),
      createVariable({name: 'mwst', value: '124.26'}),
    ]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.getByText('"value-preview"')).toBeInTheDocument();

    mockFetchVariable().withDelay(
      createVariable({
        name: 'clientNo',
        value: '"full-value"',
        isPreview: false,
      }),
    );

    await user.click(
      within(screen.getByTestId('variable-clientNo')).getByRole('button', {
        name: /edit variable/i,
      }),
    );
    expect(screen.getByTestId('full-variable-loader')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('variable-mwst')).getByRole('button', {
        name: /edit variable/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(screen.getByTestId('full-variable-loader'));

    expect(screen.queryByText('"value-preview"')).not.toBeInTheDocument();

    expect(screen.getByTestId('edit-variable-value')).toHaveValue(
      '"full-value"',
    );
    expect(
      within(screen.getByTestId('variable-mwst')).getByRole('button', {
        name: /edit variable/i,
      }),
    ).toBeEnabled();

    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();
  });

  it('should display notification if error occurs when getting single variable details', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess([
      createVariable({isPreview: true, value: '"value-preview"'}),
    ]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.getByText('"value-preview"')).toBeInTheDocument();

    mockFetchVariable().withDelayedServerError();

    await user.click(
      within(screen.getByTestId('variable-testVariableName')).getByRole(
        'button',
        {
          name: /edit variable/i,
        },
      ),
    );
    expect(screen.getByTestId('full-variable-loader')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('full-variable-loader'));

    expect(screen.getByText('"value-preview"')).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Variable could not be fetched',
    });
  });

  it('should not get variable details on edit button click if the variables value was not a preview', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable({value: '"full-value"'})]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.getByText('"full-value"')).toBeInTheDocument();

    await user.click(
      within(screen.getByTestId('variable-testVariableName')).getByRole(
        'button',
        {
          name: /edit variable/i,
        },
      ),
    );

    expect(
      screen.queryByTestId('full-variable-loader'),
    ).not.toBeInTheDocument();
  });

  it('should load full value on focus during modification mode if it was truncated', async () => {
    jest.useFakeTimers();
    modificationsStore.enableModificationMode();
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([
      createVariable({isPreview: true, value: '123'}),
    ]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables isVariableModificationAllowed />, {
      wrapper: Wrapper,
    });
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');

    mockFetchVariable().withSuccess(
      createVariable({isPreview: false, value: '123456'}),
    );

    await user.click(screen.getByTestId('edit-variable-value'));

    expect(screen.getByTestId('full-variable-loader')).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('full-variable-loader'),
    );

    expect(screen.getByTestId('edit-variable-value')).toHaveValue('123456');

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should load full value on json viewer click during modification mode if it was truncated', async () => {
    modificationsStore.enableModificationMode();
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([
      createVariable({isPreview: true, value: '123'}),
    ]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables isVariableModificationAllowed />, {
      wrapper: Wrapper,
    });
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');

    mockFetchVariable().withSuccess(
      createVariable({isPreview: false, value: '123456'}),
    );

    await user.click(
      screen.getByRole('button', {name: /open json editor modal/i}),
    );

    await waitFor(() =>
      expect(screen.getByTestId('monaco-editor')).toHaveValue('123456'),
    );
  });

  it('should have JSON editor when editing a Variable', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess([createVariable()]);
    mockFetchVariable().withSuccess(mockVariables[0]!);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    await user.click(screen.getByRole('button', {name: /edit variable/i}));
    await user.click(
      screen.getByRole('button', {name: /open json editor modal/i}),
    );

    expect(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: /cancel/i,
      }),
    ).toBeEnabled();
    expect(
      within(screen.getByRole('dialog')).getByRole('button', {name: /apply/i}),
    ).toBeEnabled();
    expect(
      within(screen.getByRole('dialog')).getByTestId('monaco-editor'),
    ).toBeInTheDocument();
  });
});
