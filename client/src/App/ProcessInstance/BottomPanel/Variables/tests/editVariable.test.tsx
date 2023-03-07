/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {act} from 'react-dom/test-utils';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    const [activeOperationVariable] = variablesStore.state.items.filter(
      ({hasActiveOperation}) => hasActiveOperation
    );

    expect(
      within(
        // @ts-expect-error ts-migrate(2345) FIXME: Type 'null' is not assignable to type 'HTMLElement... Remove this comment to see the full error message
        screen.queryByTestId(activeOperationVariable.name)
      ).queryByTestId('edit-variable-button')
    ).not.toBeInTheDocument();

    const [inactiveOperationVariable] = variablesStore.state.items.filter(
      ({hasActiveOperation}) => !hasActiveOperation
    );

    expect(inactiveOperationVariable).toBeDefined();
    expect(
      within(screen.getByTestId(inactiveOperationVariable!.name)).getByTestId(
        'edit-variable-button'
      )
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    const [inactiveOperationVariable] = variablesStore.state.items.filter(
      ({hasActiveOperation}) => !hasActiveOperation
    );

    expect(inactiveOperationVariable).toBeDefined();
    expect(
      within(screen.getByTestId(inactiveOperationVariable!.name)).getByTestId(
        'edit-variable-button'
      )
    ).toBeInTheDocument();

    act(() =>
      processInstanceDetailsStore.setProcessInstance({
        ...instanceMock,
        state: 'CANCELED',
      })
    );

    expect(
      screen.queryByTestId('edit-variable-button')
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.queryByTestId('add-variable-value')).not.toBeInTheDocument();

    const [firstVariable] = variablesStore.state.items;
    expect(firstVariable).toBeDefined();
    const withinFirstVariable = within(screen.getByTestId(firstVariable!.name));
    expect(
      withinFirstVariable.queryByTestId('edit-variable-value')
    ).not.toBeInTheDocument();
    expect(
      withinFirstVariable.queryByTitle(/exit edit mode/i)
    ).not.toBeInTheDocument();
    expect(
      withinFirstVariable.queryByTitle(/save variable/i)
    ).not.toBeInTheDocument();

    await user.click(withinFirstVariable.getByTestId('edit-variable-button'));

    expect(
      withinFirstVariable.getByTestId('edit-variable-value')
    ).toBeInTheDocument();
    expect(
      withinFirstVariable.getByTitle(/exit edit mode/i)
    ).toBeInTheDocument();
    expect(
      withinFirstVariable.getByTitle(/save variable/i)
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.queryByTestId('add-variable-value')).not.toBeInTheDocument();

    const [firstVariable] = variablesStore.state.items;
    expect(firstVariable).toBeDefined();
    const withinFirstVariable = within(screen.getByTestId(firstVariable!.name));

    await user.click(withinFirstVariable.getByTestId('edit-variable-button'));

    expect(withinFirstVariable.getByTitle(/save variable/i)).toBeDisabled();
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();

    const [firstVariable] = variablesStore.state.items;
    expect(firstVariable).toBeDefined();
    const withinFirstVariable = within(screen.getByTestId(firstVariable!.name));

    await user.click(withinFirstVariable.getByTestId('edit-variable-button'));
    await user.type(
      screen.getByTestId('edit-variable-value'),
      "{{invalidKey: 'value'}}"
    );

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();
    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    await user.clear(screen.getByTestId('edit-variable-value'));
    await user.type(screen.getByTestId('edit-variable-value'), '123');

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByText('"value-preview"')).toBeInTheDocument();

    mockFetchVariable().withSuccess(
      createVariable({
        name: 'clientNo',
        value: '"full-value"',
        isPreview: false,
      })
    );

    await user.click(
      within(screen.getByTestId('clientNo')).getByTitle(/enter edit mode/i)
    );
    expect(screen.getByTestId('variable-backdrop')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('mwst')).getByTitle(/enter edit mode/i)
    ).toBeDisabled();

    await waitForElementToBeRemoved(screen.getByTestId('variable-backdrop'));

    expect(screen.queryByText('"value-preview"')).not.toBeInTheDocument();

    expect(screen.getByTestId('edit-variable-value')).toHaveValue(
      '"full-value"'
    );
    expect(
      within(screen.getByTestId('mwst')).getByTitle(/enter edit mode/i)
    ).toBeEnabled();
    expect(mockDisplayNotification).not.toHaveBeenCalled();
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByText('"value-preview"')).toBeInTheDocument();

    mockFetchVariable().withServerError();

    await user.click(
      within(screen.getByTestId('testVariableName')).getByTitle(
        /enter edit mode/i
      )
    );
    expect(screen.getByTestId('variable-backdrop')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('variable-backdrop'));

    expect(screen.getByText('"value-preview"')).toBeInTheDocument();

    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Variable could not be fetched',
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByText('"full-value"')).toBeInTheDocument();

    await user.click(
      within(screen.getByTestId('testVariableName')).getByTitle(
        /enter edit mode/i
      )
    );

    expect(screen.queryByTestId('variable-backdrop')).not.toBeInTheDocument();
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');

    mockFetchVariable().withSuccess(
      createVariable({isPreview: false, value: '123456'})
    );

    await user.click(screen.getByTestId('edit-variable-value'));

    expect(screen.getByTestId('variable-backdrop')).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('variable-backdrop')
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');

    mockFetchVariable().withSuccess(
      createVariable({isPreview: false, value: '123456'})
    );

    await user.click(screen.getByTitle(/open json editor modal/i));

    await waitFor(() =>
      expect(screen.getByTestId('monaco-editor')).toHaveValue('123456')
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
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await user.click(screen.getByTitle(/enter edit mode/i));
    await user.click(screen.getByTitle(/open json editor modal/i));

    expect(
      within(screen.getByTestId('modal')).getByRole('button', {name: /cancel/i})
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByRole('button', {name: /apply/i})
    ).toBeEnabled();
    expect(
      within(screen.getByTestId('modal')).getByTestId('json-editor-container')
    ).toBeInTheDocument();
  });
});
