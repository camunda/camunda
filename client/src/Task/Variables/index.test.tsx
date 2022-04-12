/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as React from 'react';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from '@testing-library/react';
import {
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {
  mockGetTaskVariables,
  mockGetTaskEmptyVariables,
  mockGetTaskVariablesTruncatedValues,
  mockGetFullVariableValue,
} from 'modules/queries/get-task-variables';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Variables} from './index';
import {claimedTask, unclaimedTask} from 'modules/mock-schema/mocks/task';
import userEvent from '@testing-library/user-event';
import {ApolloProvider, useQuery} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';
import {graphql} from 'msw';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => (
  <ApolloProvider client={client}>
    <MockThemeProvider>{children}</MockThemeProvider>
  </ApolloProvider>
);

describe('<Variables />', () => {
  beforeEach(() => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );
  });

  it('should show existing variables for unassigned tasks', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={unclaimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('myVar')).toBeInTheDocument();
    expect(screen.getByText('"0001"')).toBeInTheDocument();
    expect(screen.getByText('isCool')).toBeInTheDocument();
    expect(screen.getByText('"yes"')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('should show a message when the tasks has no variables', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Task has no Variables'),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-table')).not.toBeInTheDocument();
  });

  it('should edit variable', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );
    const newVariableValue = '"changedValue"';

    expect(await screen.findByDisplayValue('"0001"')).toBeInTheDocument();

    userEvent.clear(screen.getByDisplayValue('"0001"'));
    userEvent.type(screen.getByLabelText('myVar'), newVariableValue);

    expect(screen.getByDisplayValue(newVariableValue)).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
  });

  it('should add two variables and remove one', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/Add Variable/));
    userEvent.click(screen.getByText(/Add Variable/));

    expect(screen.getAllByPlaceholderText(/name/i)).toHaveLength(2);
    expect(screen.getAllByPlaceholderText(/value/i)).toHaveLength(2);
    expect(screen.getByLabelText('New variable 0 name')).toBeInTheDocument();
    expect(screen.getByLabelText('New variable 0 value')).toBeInTheDocument();
    expect(screen.getByLabelText('New variable 1 name')).toBeInTheDocument();
    expect(screen.getByLabelText('New variable 1 value')).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();

    userEvent.click(screen.getByLabelText('Remove new variable 1'));

    expect(screen.getAllByPlaceholderText(/name/i)).toHaveLength(1);
    expect(screen.getAllByPlaceholderText(/value/i)).toHaveLength(1);
    expect(screen.getByLabelText('New variable 0 name')).toBeInTheDocument();
    expect(screen.getByLabelText('New variable 0 value')).toBeInTheDocument();

    expect(
      screen.queryByLabelText('New variable 1 name'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText('New variable 1 value'),
    ).not.toBeInTheDocument();
  });

  it('should add variable on task without variables', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/Add Variable/));

    expect(screen.getByLabelText('New variable 0 name')).toBeInTheDocument();
    expect(screen.getByLabelText('New variable 0 value')).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should validate an empty variable name', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/Add Variable/));
    userEvent.type(
      screen.getByLabelText('New variable 0 value'),
      '"valid_value"',
    );

    expect(
      await screen.findByText('Name has to be filled'),
    ).toBeInTheDocument();
  });

  it('should validate an invalid variable name', async () => {
    jest.useFakeTimers();
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/Add Variable/));
    userEvent.type(screen.getByLabelText('New variable 0 name'), '"');
    userEvent.type(
      screen.getByLabelText('New variable 0 value'),
      '"valid_value"',
    );

    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();

    userEvent.clear(screen.getByLabelText('New variable 0 name'));

    await waitForElementToBeRemoved(() =>
      screen.queryByText('Name is invalid'),
    );

    userEvent.type(screen.getByLabelText('New variable 0 name'), 'test ');

    expect(await screen.findByText('Name is invalid')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should validate an empty variable value', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/Add Variable/));
    userEvent.type(screen.getByLabelText('New variable 0 name'), 'valid_name');

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
  });

  it('should validate an invalid variable value', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/Add Variable/));

    userEvent.type(
      screen.getByLabelText('New variable 0 value'),
      'invalid_value}}}',
    );

    expect(
      await screen.findByText('Name has to be filled'),
    ).toBeInTheDocument();
    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
  });

  it('should not validate valid variables', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/Add Variable/));
    userEvent.type(screen.getByLabelText('New variable 0 name'), 'valid_name');
    userEvent.type(
      screen.getByLabelText('New variable 0 value'),
      '"valid_value"',
    );

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    expect(
      screen.queryByTitle('Name has to be filled and Value has to be JSON'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle('Name has to be filled'),
    ).not.toBeInTheDocument();
    expect(screen.queryByTitle('Value has to be JSON')).not.toBeInTheDocument();
  });

  it('should handle submission', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();
    const {rerender} = render(
      <Variables key="id_0" task={claimedTask()} onSubmit={mockOnSubmit} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/complete task/i));

    await waitFor(() => expect(mockOnSubmit).toHaveBeenNthCalledWith(1, []));
    expect(mockOnSubmit).toHaveBeenCalledTimes(1);

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(screen.getByLabelText('New variable 0 name'), 'var');
    userEvent.type(screen.getByLabelText('New variable 0 value'), '1');

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    userEvent.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenNthCalledWith(2, [
        {
          name: 'var',
          value: '1',
        },
      ]),
    );
    expect(mockOnSubmit).toHaveBeenCalledTimes(2);

    rerender(
      <Variables key="id_1" task={claimedTask()} onSubmit={mockOnSubmit} />,
    );

    expect(await screen.findByLabelText('myVar')).toBeInTheDocument();

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(screen.getByLabelText('New variable 0 name'), 'name');
    userEvent.type(screen.getByLabelText('New variable 0 value'), '"Jon"');

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    userEvent.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenNthCalledWith(3, [
        {
          name: 'name',
          value: '"Jon"',
        },
      ]),
    );
    expect(mockOnSubmit).toHaveBeenCalledTimes(3);
  });

  it('should change variable and complete task', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();

    render(<Variables task={claimedTask()} onSubmit={mockOnSubmit} />, {
      wrapper: Wrapper,
    });

    userEvent.clear(await screen.findByLabelText('myVar'));
    userEvent.type(screen.getByLabelText('myVar'), '"newValue"');
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    userEvent.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'myVar',
          value: '"newValue"',
        },
      ]),
    );
  });

  it('should not be able to change variable, add variable and complete task if user has no permission', async () => {
    const UserName = () => {
      const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

      return <div>{data?.currentUser.displayName}</div>;
    };

    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();

    render(
      <>
        <UserName />
        <Variables task={claimedTask()} onSubmit={mockOnSubmit} />
      </>,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(await screen.findByText(/myVar/)).toBeInTheDocument();

    expect(screen.queryByText('Add Variable')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('myVar')).not.toBeInTheDocument();
    expect(screen.queryByText(/complete task/i)).not.toBeInTheDocument();
  });

  it('should add new variable and complete task', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();

    render(<Variables task={claimedTask()} onSubmit={mockOnSubmit} />, {
      wrapper: Wrapper,
    });

    userEvent.click(await screen.findByText('Add Variable'));
    userEvent.type(
      screen.getByLabelText('New variable 0 name'),
      'newVariableName',
    );
    userEvent.type(
      screen.getByLabelText('New variable 0 value'),
      '"newVariableValue"',
    );

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
    userEvent.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'newVariableName',
          value: '"newVariableValue"',
        },
      ]),
    );
  });

  it('should disable submit button on form errors for existing variables', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.type(await screen.findByLabelText('myVar'), '{{ invalid value');

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable submit button on form errors for new variables', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(
      screen.getByLabelText('New variable 0 value'),
      '{{ invalid value',
    );

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable completion button', async () => {
    mockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should complete a task with a truncated variable', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetTaskVariablesTruncatedValues().result.data),
        );
      }),
      graphql.query('GetFullVariableValue', (_, res, ctx) => {
        return res.once(ctx.data(mockGetFullVariableValue().result.data));
      }),
    );
    const mockOnSubmit = jest.fn();
    render(<Variables task={claimedTask()} onSubmit={mockOnSubmit} />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByDisplayValue('"000')).toBeInTheDocument();

    userEvent.click(screen.getByDisplayValue('"000'));

    expect(screen.getByTestId('textarea-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('textarea-loading-overlay'),
    );

    expect(screen.getByDisplayValue('"0001"')).toBeInTheDocument();

    userEvent.clear(screen.getByDisplayValue('"0001"'));
    userEvent.type(screen.getByLabelText('myVar'), '"newVariableValue"');
    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );
    userEvent.click(screen.getByText(/complete task/i));

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'myVar',
          value: '"newVariableValue"',
        },
      ]),
    );
  });

  it('should preserve full value', async () => {
    const mockVariable = {id: '1-myVar', value: '"1112"'};
    const mockNewValue = '"new-value"';
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetTaskVariablesTruncatedValues().result.data),
        );
      }),
      graphql.query('GetFullVariableValue', (_, res, ctx) => {
        return res.once(ctx.data(mockGetFullVariableValue().result.data));
      }),
      graphql.query('GetFullVariableValue', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetFullVariableValue(mockVariable).result.data),
        );
      }),
    );
    render(
      <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByDisplayValue('"000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('"111')).toBeInTheDocument();

    userEvent.click(screen.getByDisplayValue('"000'));

    const firstVariableValueTextarea = await screen.findByDisplayValue(
      '"0001"',
    );
    expect(firstVariableValueTextarea).toBeInTheDocument();
    expect(screen.getByDisplayValue('"111')).toBeInTheDocument();

    userEvent.clear(firstVariableValueTextarea);
    userEvent.type(firstVariableValueTextarea, mockNewValue);
    userEvent.click(screen.getByDisplayValue('"111'));

    expect(
      await screen.findByDisplayValue(mockVariable.value),
    ).toBeInTheDocument();
    expect(screen.getByDisplayValue(mockNewValue)).toBeInTheDocument();
  });

  describe('Duplicate variable validations', () => {
    beforeEach(() => {
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should display error if name is the same with one of the existing variables', async () => {
      mockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/Add Variable/));

      userEvent.type(screen.getByLabelText('New variable 0 name'), 'myVar');

      expect(
        await screen.findByText('Name must be unique'),
      ).toBeInTheDocument();
      expect(
        await screen.findByText('Value has to be JSON'),
      ).toBeInTheDocument();
    });

    it('should display error on the last modified field when two new variables are added with the same name', async () => {
      mockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/Add Variable/));
      userEvent.type(screen.getByLabelText('New variable 0 name'), 'myVar2');

      expect(
        await screen.findByText('Value has to be JSON'),
      ).toBeInTheDocument();

      userEvent.click(screen.getByText(/Add Variable/));
      userEvent.type(screen.getByLabelText('New variable 1 name'), 'myVar2');

      expect(
        await within(screen.getByTestId('newVariables[1]')).findByText(
          'Name must be unique',
        ),
      ).toBeInTheDocument();

      expect(
        await within(screen.getByTestId('newVariables[1]')).findByText(
          'Value has to be JSON',
        ),
      ).toBeInTheDocument();

      expect(
        // eslint-disable-next-line testing-library/prefer-presence-queries
        within(screen.getByTestId('newVariables[0]')).queryByText(
          'Name must be unique',
        ),
      ).not.toBeInTheDocument();

      userEvent.type(screen.getByLabelText('New variable 1 name'), '3');
      await waitForElementToBeRemoved(() =>
        screen.queryByText('Name must be unique'),
      );

      userEvent.type(screen.getByLabelText('New variable 0 name'), '3');
      expect(
        await within(screen.getByTestId('newVariables[0]')).findByText(
          'Name must be unique',
        ),
      ).toBeInTheDocument();
      expect(
        // eslint-disable-next-line testing-library/prefer-presence-queries
        within(screen.getByTestId('newVariables[1]')).queryByText(
          'Name must be unique',
        ),
      ).not.toBeInTheDocument();

      userEvent.type(screen.getByLabelText('New variable 1 name'), '4');
      await waitForElementToBeRemoved(() =>
        screen.queryByText('Name must be unique'),
      );
    });

    it('should display error if duplicate name is used and immediately started typing on to the value field', async () => {
      mockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/Add Variable/));

      userEvent.type(screen.getByLabelText('New variable 0 name'), 'myVar2');
      userEvent.type(screen.getByLabelText('New variable 0 value'), '1');

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      userEvent.click(await screen.findByText(/Add Variable/));

      userEvent.type(screen.getByLabelText('New variable 1 name'), 'myVar2');
      userEvent.type(screen.getByLabelText('New variable 1 value'), '2');

      expect(
        await within(screen.getByTestId('newVariables[1]')).findByText(
          'Name must be unique',
        ),
      ).toBeInTheDocument();

      expect(
        // eslint-disable-next-line testing-library/prefer-presence-queries
        within(screen.getByTestId('newVariables[0]')).queryByText(
          'Name must be unique',
        ),
      ).not.toBeInTheDocument();
    });

    it('should continue to display existing duplicate name error', async () => {
      mockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables task={claimedTask()} onSubmit={() => Promise.resolve()} />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/Add Variable/));

      userEvent.type(screen.getByLabelText('New variable 0 name'), 'myVar2');
      userEvent.type(screen.getByLabelText('New variable 0 value'), '1');

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      userEvent.click(await screen.findByText(/Add Variable/));

      userEvent.type(screen.getByLabelText('New variable 1 name'), 'myVar2');
      userEvent.type(screen.getByLabelText('New variable 1 value'), '2');

      expect(
        await within(screen.getByTestId('newVariables[1]')).findByText(
          'Name must be unique',
        ),
      ).toBeInTheDocument();

      userEvent.click(await screen.findByText(/Add Variable/));

      userEvent.type(screen.getByLabelText('New variable 2 name'), 'myVar2');
      userEvent.type(screen.getByLabelText('New variable 2 value'), '3');

      expect(
        await within(screen.getByTestId('newVariables[2]')).findByText(
          'Name must be unique',
        ),
      ).toBeInTheDocument();

      expect(
        within(screen.getByTestId('newVariables[1]')).getByText(
          'Name must be unique',
        ),
      ).toBeInTheDocument();

      expect(
        // eslint-disable-next-line testing-library/prefer-presence-queries
        within(screen.getByTestId('newVariables[0]')).queryByText(
          'Name must be unique',
        ),
      ).not.toBeInTheDocument();
    });
  });
});
