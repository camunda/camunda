/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
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
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {graphql} from 'msw';
import {noop} from 'lodash';

const {currentUser} = mockGetCurrentUser.result.data;

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
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should show existing variables for unassigned tasks', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={unclaimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('myVar')).toBeInTheDocument();
    expect(screen.getByText('"0001"')).toBeInTheDocument();
    expect(screen.getByText('isCool')).toBeInTheDocument();
    expect(screen.getByText('"yes"')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('should show a message when the tasks has no variables', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('Task has no Variables'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('variables-form-table'),
    ).not.toBeInTheDocument();
  });

  it('should edit variable', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
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
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.click(screen.getByText(/add variable/i));

    expect(screen.getAllByPlaceholderText(/^name$/i)).toHaveLength(2);
    expect(screen.getAllByPlaceholderText(/^value$/i)).toHaveLength(2);
    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/2nd variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/2nd variable value/i)).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();

    userEvent.click(screen.getByLabelText(/remove 2nd new variable/i));

    expect(screen.getAllByPlaceholderText(/^name$/i)).toHaveLength(1);
    expect(screen.getAllByPlaceholderText(/^value$/i)).toHaveLength(1);
    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();

    expect(
      screen.queryByLabelText(/2nd variable name/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/2nd variable value/i),
    ).not.toBeInTheDocument();
  });

  it('should add variable on task without variables', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));

    expect(screen.getByLabelText(/1st variable name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/1st variable value/i)).toBeInTheDocument();

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should validate an empty variable name', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name has to be filled/i),
    );
  });

  it('should validate an invalid variable name', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(screen.getByLabelText(/1st variable name/i), '"');
    userEvent.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name is invalid/i),
    );

    userEvent.clear(screen.getByLabelText(/1st variable name/i));

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name is invalid/i),
    );

    userEvent.type(screen.getByLabelText(/1st variable name/i), 'test ');

    expect(await screen.findByText(/name is invalid/i)).toBeInTheDocument();
  });

  it('should validate an empty variable value', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(screen.getByLabelText(/1st variable name/i), 'valid_name');

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable value/i),
      ).toHaveAccessibleDescription(/value has to be json or a literal/i),
    );
  });

  it('should validate an invalid variable value', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));

    userEvent.type(
      screen.getByLabelText(/1st variable value/i),
      'invalid_value}}}',
    );

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name has to be filled/i),
    );
    expect(
      screen.getByLabelText(/1st variable value/i),
    ).toHaveAccessibleDescription(/value has to be json or a literal/i);
  });

  it('should not validate valid variables', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(screen.getByLabelText(/1st variable name/i), 'valid_name');
    userEvent.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    expect(
      screen.queryByTitle(
        /name has to filled and value has to be json or a literal/i,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/name has to be filled/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/value has to be json or a literal/i),
    ).not.toBeInTheDocument();
  });

  it('should handle submission', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();
    const {rerender} = render(
      <Variables
        key="id_0"
        task={claimedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    userEvent.click(await screen.findByText(/complete task/i));

    expect(screen.getByText('Completing task...'));
    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(1);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(1, []);

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(screen.getByLabelText(/1st variable name/i), 'var');
    userEvent.type(screen.getByLabelText(/1st variable value/i), '1');

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    userEvent.click(screen.getByText(/complete task/i));

    expect(screen.getByText('Completing task...'));
    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(2);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(2, [
      {
        name: 'var',
        value: '1',
      },
    ]);

    rerender(
      <Variables
        key="id_1"
        task={claimedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
    );

    expect(await screen.findByLabelText('myVar')).toBeInTheDocument();

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(screen.getByLabelText(/1st variable name/i), 'name');
    userEvent.type(screen.getByLabelText(/1st variable value/i), '"Jon"');

    await waitFor(() =>
      expect(screen.getByText(/complete task/i)).toBeEnabled(),
    );

    userEvent.click(screen.getByText(/complete task/i));

    expect(screen.getByText('Completing task...'));
    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalledTimes(3);
    expect(mockOnSubmit).toHaveBeenNthCalledWith(3, [
      {
        name: 'name',
        value: '"Jon"',
      },
    ]);
  });

  it('should change variable and complete task', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

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

    const restrictedUser = mockGetCurrentRestrictedUser.result.data;

    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(restrictedUser));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();

    render(
      <>
        <UserName />
        <Variables
          task={claimedTask()}
          user={restrictedUser.currentUser}
          onSubmit={mockOnSubmit}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />
      </>,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(await screen.findByText(/myVar/)).toBeInTheDocument();

    expect(screen.queryByText(/add variable/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText('myVar')).not.toBeInTheDocument();
    expect(screen.queryByText(/complete task/i)).not.toBeInTheDocument();
  });

  it('should add new variable and complete task', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const mockOnSubmit = jest.fn();

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(
      screen.getByLabelText(/1st variable name/i),
      'newVariableName',
    );
    userEvent.type(
      screen.getByLabelText(/1st variable value/i),
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
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.type(await screen.findByLabelText('myVar'), '{{ invalid value');

    await waitFor(() =>
      expect(screen.getByLabelText('myVar')).toHaveAccessibleDescription(
        /value has to be json or a literal/i,
      ),
    );

    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable submit button on form errors for new variables', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));
    userEvent.type(
      screen.getByLabelText(/1st variable value/i),
      '{{ invalid value',
    );

    await waitFor(() =>
      expect(
        screen.getByLabelText(/1st variable value/i),
      ).toHaveAccessibleDescription(/value has to be json or a literal/i),
    );

    expect(screen.getByText(/complete task/i)).toBeDisabled();
  });

  it('should disable completion button', async () => {
    nodeMockServer.use(
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.click(await screen.findByText(/add variable/i));

    expect(await screen.findByText(/complete task/i)).toBeDisabled();
  });

  it('should complete a task with a truncated variable', async () => {
    nodeMockServer.use(
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
    render(
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={mockOnSubmit}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
      {
        wrapper: Wrapper,
      },
    );

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
    nodeMockServer.use(
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
      <Variables
        task={claimedTask()}
        user={currentUser}
        onSubmit={() => Promise.resolve()}
        onSubmitFailure={noop}
        onSubmitSuccess={noop}
      />,
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
    it('should display error if name is the same with one of the existing variables', async () => {
      nodeMockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables
          task={claimedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/add variable/i));
      userEvent.type(screen.getByLabelText(/1st variable name/i), 'myVar');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/1st variable value/i),
        ).toHaveAccessibleDescription(/value has to be json or a literal/i),
      );
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should display duplicate name error on last edited variable', async () => {
      nodeMockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables
          task={claimedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/add variable/i));
      userEvent.type(screen.getByLabelText(/1st variable name/i), 'myVar2');

      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);

      userEvent.click(screen.getByText(/add variable/i));
      userEvent.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      userEvent.type(screen.getByLabelText(/2nd variable name/i), 'foo');

      expect(
        screen.getByLabelText(/2nd variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);

      userEvent.type(screen.getByLabelText(/1st variable name/i), 'foo');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/1st variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );
      expect(
        screen.getByLabelText(/2nd variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should display error if duplicate name is used and immediately started typing on to the value field', async () => {
      nodeMockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables
          task={claimedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/add variable/i));

      userEvent.type(screen.getByLabelText(/1st variable name/i), 'myVar2');
      userEvent.type(screen.getByLabelText(/1st variable value/i), '1');

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      userEvent.click(await screen.findByText(/add variable/i));

      userEvent.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');
      userEvent.type(screen.getByLabelText(/2nd variable value/i), '2');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });

    it('should continue to display existing duplicate name error', async () => {
      nodeMockServer.use(
        graphql.query('GetTaskVariables', (_, res, ctx) => {
          return res.once(ctx.data(mockGetTaskVariables().result.data));
        }),
      );

      render(
        <Variables
          task={claimedTask()}
          user={currentUser}
          onSubmit={() => Promise.resolve()}
          onSubmitFailure={noop}
          onSubmitSuccess={noop}
        />,
        {
          wrapper: Wrapper,
        },
      );

      userEvent.click(await screen.findByText(/add variable/i));

      userEvent.type(screen.getByLabelText(/1st variable name/i), 'myVar2');
      userEvent.type(screen.getByLabelText(/1st variable value/i), '1');

      await waitFor(() =>
        expect(screen.getByText(/complete task/i)).toBeEnabled(),
      );

      userEvent.click(screen.getByText(/add variable/i));

      userEvent.type(screen.getByLabelText(/2nd variable name/i), 'myVar2');
      userEvent.type(screen.getByLabelText(/2nd variable value/i), '2');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/2nd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      userEvent.click(screen.getByText(/add variable/i));

      userEvent.type(screen.getByLabelText(/3rd variable name/i), 'myVar2');
      userEvent.type(screen.getByLabelText(/3rd variable value/i), '3');

      await waitFor(() =>
        expect(
          screen.getByLabelText(/3rd variable name/i),
        ).toHaveAccessibleDescription(/name must be unique/i),
      );

      expect(
        screen.getByLabelText(/2nd variable name/i),
      ).toHaveAccessibleDescription(/name must be unique/i);
      expect(
        screen.getByLabelText(/1st variable name/i),
      ).not.toHaveAccessibleDescription(/name must be unique/i);
    });
  });
});
