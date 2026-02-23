/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within, waitFor} from 'modules/testing-library';
import {getWrapper, mockProcessInstance} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockVariables} from './index.setup';
import {VariablePanel} from '../index';

describe('Add variable', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  });

  it('should show/hide add variable inputs', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {
        wrapper: getWrapper(),
      },
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    // clicks are not doing anything because we mock context value... we need to either move the test elsewhere or mock the context properly
    expect(
      await screen.findByRole('textbox', {name: /name/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /exit edit mode/i}));

    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not allow empty value', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
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

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /save variable/i}),
      ).toBeDisabled(),
    );

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'test',
    );
    expect(screen.getByRole('button', {name: /save variable/i})).toBeDisabled();

    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '    ',
    );

    expect(screen.getByRole('button', {name: /save variable/i})).toBeDisabled();
    expect(screen.queryByTitle('Value has to be JSON')).not.toBeInTheDocument();

    vi.runOnlyPendingTimers();

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not allow empty characters in variable name', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
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

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      await screen.findByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();

    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '123',
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    vi.runOnlyPendingTimers();
    expect(
      await screen.findByText('Name has to be filled'),
    ).toBeInTheDocument();

    await user.dblClick(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      'test',
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();

    expect(screen.getByText('Name has to be filled')).toBeInTheDocument();
    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    vi.runOnlyPendingTimers();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      '   ',
    );

    expect(screen.getByText('Value has to be JSON')).toBeInTheDocument();
    vi.runOnlyPendingTimers();
    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      ' test',
    );

    expect(screen.getByText('Value has to be JSON')).toBeInTheDocument();
    expect(screen.getByText('Name is invalid')).toBeInTheDocument();

    await user.dblClick(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"valid value"',
    );

    expect(screen.queryByText('Value has to be JSON')).not.toBeInTheDocument();
    expect(screen.getByText('Name is invalid')).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not allow to add duplicate variables', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
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

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      mockVariables.items[0]!.name,
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    expect(screen.queryByText('Name should be unique')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Value has to be filled'),
    ).not.toBeInTheDocument();
    vi.runOnlyPendingTimers();
    expect(
      await screen.findByText('Name should be unique'),
    ).toBeInTheDocument();
    expect(screen.getByText('Value has to be filled')).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '123',
    );

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    expect(
      screen.queryByText('Value has to be filled'),
    ).not.toBeInTheDocument();

    expect(screen.getByText('Name should be unique')).toBeInTheDocument();

    await user.dblClick(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'someOtherName',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    expect(screen.queryByText('Name should be unique')).not.toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should not allow to add variable with invalid name', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
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

    await waitFor(() => {
      expect(screen.getByRole('button', {name: /add variable/i})).toBeEnabled();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      '"invalid"',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '123',
    );

    vi.runOnlyPendingTimers();

    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    ).toBeDisabled();

    await user.clear(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'someOtherName',
    );

    vi.runOnlyPendingTimers();

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    expect(screen.queryByText('Name is invalid')).not.toBeInTheDocument();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('clicking edit variables while add mode is open, should not display a validation error', async () => {
    mockSearchVariables().withSuccess(mockVariables);
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

    mockFetchProcessDefinitionXml().withSuccess('');
    await user.click(screen.getByRole('button', {name: /add variable/i}));

    await waitFor(() => {
      expect(screen.getByTestId('variable-clientNo')).toBeInTheDocument();
    });
    const withinVariable = within(screen.getByTestId('variable-clientNo'));
    await user.click(
      withinVariable.getByRole('button', {name: /edit variable/i}),
    );
    expect(
      screen.queryByTitle('Name should be unique'),
    ).not.toBeInTheDocument();
  });

  it('should not exit add variable state when user presses Enter', async () => {
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

    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /add variable/i}));

    expect(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    ).toBeInTheDocument();

    await user.keyboard('{Enter}');

    expect(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
    ).toBeInTheDocument();
  });

  it('should exit Add Variable mode when process is canceled', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'TERMINATED',
    });
    mockSearchVariables().withSuccess(mockVariables);
    mockSearchVariables().withSuccess(mockVariables);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', {name: /add variable/i}));
    expect(
      screen.queryByRole('textbox', {
        name: /name/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', {
        name: /value/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should have JSON editor when adding a new Variable', async () => {
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

    expect(
      await screen.findByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /add variable/i}));
    await user.click(screen.getByRole('button', {name: /open json editor/i}));

    expect(
      within(screen.getByRole('dialog')).getByRole('button', {name: /cancel/i}),
    ).toBeEnabled();
    expect(
      within(screen.getByRole('dialog')).getByRole('button', {name: /apply/i}),
    ).toBeEnabled();
    expect(
      await within(screen.getByRole('dialog')).findByTestId('monaco-editor'),
    ).toBeInTheDocument();
  });
});
