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
import {getWrapper, mockProcessInstance} from './mocks';
import {createVariable} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockVariables} from './index.setup';
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

describe('Edit variable', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  });

  it('should disable edit buttons while a variable update is being submitted', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const mockVariables = {
      items: [
        createVariable({
          name: 'firstVariable',
          value: '"initial-value"',
          isTruncated: false,
        }),
        createVariable({
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
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

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

    mockUpdateElementInstanceVariables('1').withDelay(null);
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
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);
    mockGetVariable().withSuccess(mockVariables.items[0]!);
    mockGetVariable().withSuccess(mockVariables.items[0]!);

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
    mockGetVariable().withSuccess(mockVariables.items[0]!);
    mockGetVariable().withSuccess(mockVariables.items[0]!);
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

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
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);
    mockGetVariable().withSuccess(mockVariables.items[0]!);
    mockGetVariable().withSuccess(mockVariables.items[0]!);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    vi.useFakeTimers({shouldAdvanceTime: true});

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
        createVariable({
          name: 'clientNo',
          value: '"value-preview"',
          isTruncated: true,
        }),
        createVariable({
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
        createVariable({
          name: 'clientNo',
          value: '"value-preview"',
          isTruncated: true,
        }),
        createVariable({
          name: 'mwst',
          value: '"124.26"',
        }),
      ],
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
    await waitFor(() => {
      expect(screen.getByText('"value-preview"')).toBeInTheDocument();
    });

    mockGetVariable().withSuccess(
      createVariable({
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
        createVariable({
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
        createVariable({
          value: '"value-preview"',
          isTruncated: true,
        }),
      ],
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
        createVariable({
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
        createVariable({
          value: '"full-value"',
          isTruncated: false,
        }),
      ],
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
    mockFetchProcessDefinitionXml().withSuccess('');

    mockSearchVariables().withSuccess({
      items: [
        createVariable({
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
        createVariable({
          value: '123',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockGetVariable().withSuccess(
      createVariable({
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
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {id: 'flow_node_0', name: 'flow node 0'},
          scopeId: 'random-scope-id-0',
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {},
        },
      });
    });
    await waitFor(() => {
      expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');
    });

    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(screen.getByTestId('edit-variable-value'));

    expect(screen.getByTestId('edit-variable-value')).toHaveValue('123456');
  });

  it('should load full value on json viewer click during modification mode if it was truncated', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchVariables().withSuccess({
      items: [
        createVariable({
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
        createVariable({
          value: '123',
          isTruncated: true,
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockGetVariable().withSuccess(
      createVariable({
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
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {id: 'flow_node_0', name: 'flow node 0'},
          scopeId: 'random-scope-id-0',
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {},
        },
      });
    });
    await waitFor(() => {
      expect(screen.getByTestId('edit-variable-value')).toHaveValue('123');
    });

    mockGetVariable().withSuccess(
      createVariable({
        value: '123456',
        isTruncated: false,
      }),
    );

    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(screen.getByRole('button', {name: /open json editor/i}));

    await waitFor(() =>
      expect(screen.getByTestId('monaco-editor')).toHaveValue('123456'),
    );
  });

  it('should have JSON editor when editing a Variable', async () => {
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
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      await screen.findByRole('button', {name: /edit variable/i}),
    ).toBeInTheDocument();
    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(screen.getByRole('button', {name: /edit variable/i}));
    await user.click(screen.getByRole('button', {name: /open json editor/i}));

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

  it('should allow editing a variable with dots in modification mode', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');

    mockSearchVariables().withSuccess({
      items: [
        createVariable({name: 'a.b', value: '"old"', isTruncated: false}),
        createVariable({name: 'a.b.c', value: '"old"', isTruncated: false}),
      ],
      page: {totalItems: 2},
    });
    mockSearchVariables().withSuccess({
      items: [
        createVariable({name: 'a.b', value: '"old"', isTruncated: false}),
        createVariable({name: 'a.b.c', value: '"old"', isTruncated: false}),
      ],
      page: {totalItems: 2},
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    act(() => {
      modificationsStore.enableModificationMode();
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {id: 'flow_node_0', name: 'flow node 0'},
          scopeId: 'random-scope-id-0',
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {},
        },
      });
    });

    const variableRow = await screen.findByTestId('variable-a.b');
    const input = within(variableRow).getByTestId('edit-variable-value');
    expect(input).toHaveValue('"old"');

    await user.clear(input);
    await user.type(input, '"new"');

    await waitFor(() => {
      expect(input).toHaveValue('"new"');
    });
  });

  it('should not display edit button next to variables if instance is completed or canceled', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'TERMINATED',
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
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
