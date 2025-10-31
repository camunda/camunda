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
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {
  getWrapper,
  mockProcessInstance,
  mockProcessInstanceDeprecated,
} from './mocks';
import {createInstance, createVariableV2} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockVariablesV2} from './index.setup';
import {mockGetVariable} from 'modules/mocks/api/v2/variables/getVariable';
import {VariablePanel} from '../index';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockUpdateElementInstanceVariables} from 'modules/mocks/api/v2/elementInstances/updateElementInstanceVariables';
import {act} from 'react';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const instanceMock = createInstance({id: '1'});

describe('Edit variable', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchVariables().withSuccess(mockVariablesV2);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  });

  it('should disable edit buttons while a variable update is being submitted', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const mockVariables = {
      items: [
        createVariableV2({
          name: 'firstVariable',
          value: '"initial-value"',
          isTruncated: false,
        }),
        createVariableV2({
          name: 'secondVariable',
          value: '"another-value"',
          isTruncated: false,
        }),
      ],
      page: {
        totalItems: 2,
      },
    };

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    const firstVariableContainer = screen.getByTestId('variable-firstVariable');
    const secondVariableContainer = screen.getByTestId(
      'variable-secondVariable',
    );

    const firstEditButton = within(firstVariableContainer).getByRole('button', {
      name: /edit variable/i,
    });
    const secondEditButton = within(secondVariableContainer).getByRole(
      'button',
      {name: /edit variable/i},
    );

    expect(firstEditButton).toBeEnabled();
    expect(secondEditButton).toBeEnabled();

    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(firstEditButton);

    const editInput = await within(firstVariableContainer).findByTestId(
      'edit-variable-value',
    );
    await user.clear(editInput);
    await user.type(editInput, '"updated-value"');

    vi.runOnlyPendingTimers();

    const saveButton = within(firstVariableContainer).getByRole('button', {
      name: /save variable/i,
    });
    await waitFor(() => {
      expect(saveButton).toBeEnabled();
    });

    mockUpdateElementInstanceVariables('1').withDelay(null as unknown as never);
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(saveButton);

    expect(
      within(firstVariableContainer).getByTestId('full-variable-loader'),
    ).toBeInTheDocument();
    expect(secondEditButton).toBeDisabled();

    await waitForElementToBeRemoved(
      within(firstVariableContainer).queryByTestId('full-variable-loader'),
    );

    expect(secondEditButton).toBeEnabled();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should show/hide edit variable inputs', async () => {
    mockSearchVariables().withSuccess(mockVariablesV2);
    mockSearchVariables().withSuccess(mockVariablesV2);
    mockGetVariable().withSuccess(mockVariablesV2.items[0]!);
    mockGetVariable().withSuccess(mockVariablesV2.items[0]!);
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(screen.queryByTestId('add-variable-value')).not.toBeInTheDocument();

    expect(await screen.findByTestId(`variable-clientNo`)).toBeInTheDocument();
    const withinFirstVariable = within(screen.getByTestId(`variable-clientNo`));
    expect(
      withinFirstVariable.queryByTestId('edit-variable-value'),
    ).not.toBeInTheDocument();
    expect(
      withinFirstVariable.queryByRole('button', {name: /exit edit mode/i}),
    ).not.toBeInTheDocument();
    expect(
      withinFirstVariable.queryByRole('button', {name: /save variable/i}),
    ).not.toBeInTheDocument();

    expect(
      await withinFirstVariable.findByRole('button', {name: /edit variable/i}),
    ).toBeInTheDocument();
    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(
      withinFirstVariable.getByRole('button', {name: /edit variable/i}),
    );

    expect(
      await withinFirstVariable.findByTestId('edit-variable-value'),
    ).toBeInTheDocument();
    expect(
      withinFirstVariable.getByRole('button', {name: /exit edit mode/i}),
    ).toBeInTheDocument();
    expect(
      withinFirstVariable.getByRole('button', {name: /save variable/i}),
    ).toBeInTheDocument();
  });

  it('should disable save button when nothing is changed', async () => {
    mockGetVariable().withSuccess(mockVariablesV2.items[0]!);
    mockGetVariable().withSuccess(mockVariablesV2.items[0]!);
    mockSearchVariables().withSuccess(mockVariablesV2);
    mockSearchVariables().withSuccess(mockVariablesV2);
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(screen.queryByTestId('add-variable-value')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId(`variable-clientNo`)).toBeInTheDocument();
    });
    const withinFirstVariable = within(screen.getByTestId(`variable-clientNo`));

    expect(
      await withinFirstVariable.findByRole('button', {name: /edit variable/i}),
    ).toBeInTheDocument();
    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(
      withinFirstVariable.getByRole('button', {name: /edit variable/i}),
    );

    expect(
      withinFirstVariable.getByRole('button', {name: /save variable/i}),
    ).toBeDisabled();
  });

  it('should validate when editing variables', async () => {
    mockSearchVariables().withSuccess(mockVariablesV2);
    mockSearchVariables().withSuccess(mockVariablesV2);
    mockGetVariable().withSuccess(mockVariablesV2.items[0]!);
    mockGetVariable().withSuccess(mockVariablesV2.items[0]!);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );

    vi.useFakeTimers({shouldAdvanceTime: true});
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId(`variable-clientNo`)).toBeInTheDocument();
    });
    const withinFirstVariable = within(screen.getByTestId(`variable-clientNo`));

    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(
      withinFirstVariable.getByRole('button', {name: /edit variable/i}),
    );

    expect(
      await screen.findByTestId('edit-variable-value'),
    ).toBeInTheDocument();
    await user.type(
      screen.getByTestId('edit-variable-value'),
      "{{invalidKey: 'value'}}",
    );

    expect(screen.getByRole('button', {name: /save variable/i})).toBeDisabled();
    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    vi.runOnlyPendingTimers();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    await user.clear(screen.getByTestId('edit-variable-value'));
    await user.type(screen.getByTestId('edit-variable-value'), '123');

    vi.runOnlyPendingTimers();
    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /save variable/i}),
      ).toBeEnabled(),
    );

    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should get variable details on edit button click if the variables value was a preview', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          name: 'clientNo',
          value: '"value-preview"',
          isTruncated: true,
        }),
        createVariableV2({
          name: 'mwst',
          value: '"124.26"',
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          name: 'clientNo',
          value: '"value-preview"',
          isTruncated: true,
        }),
        createVariableV2({
          name: 'mwst',
          value: '"124.26"',
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getByText('"value-preview"')).toBeInTheDocument();
    });

    mockGetVariable().withSuccess(
      createVariableV2({
        name: 'clientNo',
        value: '"full-value"',
        isTruncated: false,
      }),
    );

    expect(await screen.findByTestId('variable-clientNo')).toBeInTheDocument();
    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(
      within(screen.getByTestId('variable-clientNo')).getByRole('button', {
        name: /edit variable/i,
      }),
    );

    expect(screen.queryByText('"value-preview"')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId('edit-variable-value')).toHaveValue(
        '"full-value"',
      );
    });
    expect(
      within(screen.getByTestId('variable-mwst')).getByRole('button', {
        name: /edit variable/i,
      }),
    ).toBeEnabled();

    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();
  });

  it('should display notification if error occurs when getting single variable details', async () => {
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '"value-preview"',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '"value-preview"',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('"value-preview"')).toBeInTheDocument();
    });

    mockGetVariable().withServerError();

    expect(
      await screen.findByTestId('variable-testVariableName'),
    ).toBeInTheDocument();
    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(
      within(screen.getByTestId('variable-testVariableName')).getByRole(
        'button',
        {
          name: /edit variable/i,
        },
      ),
    );

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Variable could not be fetched',
      });
    });
  });

  it('should not get variable details on edit button click if the variables value was not a preview', async () => {
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '"full-value"',
          isTruncated: false,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '"full-value"',
          isTruncated: false,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });

    processInstanceDetailsStore.setProcessInstance(instanceMock);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getByText('"full-value"')).toBeInTheDocument();
    });

    expect(
      await screen.findByTestId('variable-testVariableName'),
    ).toBeInTheDocument();
    mockFetchProcessDefinitionXml().withSuccess('');
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
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessDefinitionXml().withSuccess('');

    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '123',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '123',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockGetVariable().withSuccess(
      createVariableV2({
        value: '123456',
        isTruncated: false,
      }),
    );

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {
        wrapper: getWrapper(),
      },
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    act(() => {
      modificationsStore.enableModificationMode();
    });
    await waitFor(() => {
      expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');
    });

    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(screen.getByTestId('edit-variable-value'));

    expect(screen.getByTestId('edit-variable-value')).toHaveValue('123456');
  });

  it('should load full value on json viewer click during modification mode if it was truncated', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '123',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [
        createVariableV2({
          value: '123',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockGetVariable().withSuccess(
      createVariableV2({
        value: '123456',
        isTruncated: false,
      }),
    );

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {
        wrapper: getWrapper(),
      },
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    act(() => {
      modificationsStore.enableModificationMode();
    });
    await waitFor(() => {
      expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');
    });

    mockGetVariable().withSuccess(
      createVariableV2({
        value: '123456',
        isTruncated: false,
      }),
    );

    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(
      screen.getByRole('button', {name: /open json editor modal/i}),
    );

    await waitFor(() =>
      expect(screen.getByTestId('monaco-editor')).toHaveValue('123456'),
    );
  });

  it('should have JSON editor when editing a Variable', async () => {
    processInstanceDetailsStore.setProcessInstance(instanceMock);

    mockSearchVariables().withSuccess({
      items: [createVariableV2()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createVariableV2()],
      page: {
        totalItems: 1,
      },
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      await screen.findByRole('button', {name: /edit variable/i}),
    ).toBeInTheDocument();
    mockFetchProcessDefinitionXml().withSuccess('');
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

  it('should not display edit button next to variables if instance is completed or canceled', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'TERMINATED',
    });
    processInstanceDetailsStore.setProcessInstance(instanceMock);
    mockSearchVariables().withSuccess({
      items: [createVariableV2()],
      page: {
        totalItems: 1,
      },
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
  });
});
