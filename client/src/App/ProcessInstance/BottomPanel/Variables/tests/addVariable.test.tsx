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
import {createInstance} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {act} from 'react-dom/test-utils';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const instanceMock = createInstance({id: '1'});

describe('Add variable', () => {
  it('should show/hide add variable inputs', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.queryByTestId('add-variable-row')).not.toBeInTheDocument();
    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.getByTestId('add-variable-row')).toBeInTheDocument();
    await user.click(screen.getByTitle(/exit edit mode/i));
    expect(screen.queryByTestId('add-variable-row')).not.toBeInTheDocument();
  });

  it('should not allow empty value', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await user.click(screen.getByTitle(/add variable/i));

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();

    await user.type(screen.getByTestId('add-variable-name'), 'test');
    expect(screen.getByTitle(/save variable/i)).toBeDisabled();

    await user.type(screen.getByTestId('add-variable-value'), '    ');

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();
    expect(screen.queryByTitle('Value has to be JSON')).not.toBeInTheDocument();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
  });

  it('should not allow empty characters in variable name', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await user.click(screen.getByTitle(/add variable/i));

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();

    await user.type(screen.getByTestId('add-variable-value'), '123');

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();
    expect(screen.queryByText('Name has to be filled')).not.toBeInTheDocument();
    expect(
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();

    await user.dblClick(screen.getByTestId('add-variable-value'));
    await user.type(screen.getByTestId('add-variable-value'), 'test');

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();

    expect(screen.getByText('Name has to be filled')).toBeInTheDocument();
    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), '   ');

    expect(screen.getByText('Value has to be JSON')).toBeInTheDocument();
    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), ' test');

    expect(screen.getByText('Value has to be JSON')).toBeInTheDocument();
    expect(screen.getByText('Name is invalid')).toBeInTheDocument();

    await user.dblClick(screen.getByTestId('add-variable-value'));
    await user.type(screen.getByTestId('add-variable-value'), '"valid value"');

    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    expect(screen.getByText('Name is invalid')).toBeInTheDocument();
  });

  it('should not allow to add duplicate variables', async () => {
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

    await user.click(screen.getByTitle(/add variable/i));

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();
    await user.type(
      screen.getByTestId('add-variable-name'),
      mockVariables[0]!.name
    );

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();
    expect(screen.queryByText('Name should be unique')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Value has to be filled')
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('Name should be unique')
    ).toBeInTheDocument();
    expect(
      await screen.findByText('Value has to be filled')
    ).toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-value'), '123');

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();
    expect(
      screen.queryByText('Value has to be filled')
    ).not.toBeInTheDocument();

    expect(screen.getByText('Name should be unique')).toBeInTheDocument();

    await user.dblClick(screen.getByTestId('add-variable-name'));
    await user.type(screen.getByTestId('add-variable-name'), 'someOtherName');

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    expect(screen.queryByText('Name should be unique')).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not allow to add variable with invalid name', async () => {
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

    await user.click(screen.getByTitle(/add variable/i));

    expect(screen.getByTitle(/save variable/i)).toBeDisabled();
    await user.type(screen.getByTestId('add-variable-name'), '"invalid"');
    await user.type(screen.getByTestId('add-variable-value'), '123');

    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();
    expect(screen.getByTitle(/save variable/i)).toBeDisabled();

    await user.clear(screen.getByTestId('add-variable-name'));
    await user.type(screen.getByTestId('add-variable-name'), 'someOtherName');

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    expect(screen.queryByText('Name is invalid')).not.toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('clicking edit variables while add mode is open, should not display a validation error', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables('1');

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await user.click(screen.getByTitle(/add variable/i));

    const withinVariable = within(screen.getByTestId('clientNo'));
    await user.click(withinVariable.getByTestId('edit-variable-button'));
    expect(
      screen.queryByTitle('Name should be unique')
    ).not.toBeInTheDocument();
  });

  it('should not exit add variable state when user presses Enter', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.queryByTestId('add-variable-row')).not.toBeInTheDocument();
    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.getByTestId('add-variable-row')).toBeInTheDocument();

    await user.keyboard('{Enter}');
    expect(screen.getByTestId('add-variable-row')).toBeInTheDocument();
  });

  it('should exit Add Variable mode when process is canceled', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.getByTestId('add-variable-row')).toBeInTheDocument();

    act(() =>
      processInstanceDetailsStore.setProcessInstance({
        ...instanceMock,
        state: 'CANCELED',
      })
    );

    expect(screen.queryByTestId('add-variable-row')).not.toBeInTheDocument();
  });

  it('should have JSON editor when adding a new Variable', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    const {user} = render(<Variables />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    await user.click(screen.getByTitle(/add variable/i));
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
