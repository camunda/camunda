/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Task} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {
  mockGetTaskClaimed,
  mockGetTaskCompletedWithForm,
  mockGetTaskClaimedWithForm,
  mockGetTaskCompleted,
} from 'modules/queries/get-task';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {mockCompleteTask} from 'modules/mutations/complete-task';
import {mockGetAllOpenTasks} from 'modules/queries/get-tasks';
import {mockClaimTask} from 'modules/mutations/claim-task';
import {mockUnclaimTask} from 'modules/mutations/unclaim-task';
import userEvent from '@testing-library/user-event';
import {mockGetForm, mockGetInvalidForm} from 'modules/queries/get-form';
import {
  mockGetTaskVariables,
  mockGetTaskEmptyVariables,
} from 'modules/queries/get-task-variables';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {graphql} from 'msw';
import {mockServer} from 'modules/mockServer';
import {LocationLog} from 'modules/utils/LocationLog';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const getWrapper = (
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'],
) => {
  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <ApolloProvider client={client}>
        <MockThemeProvider>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path="/:id" element={children} />
              <Route path="*" element={<LocationLog />} />
            </Routes>
          </MemoryRouter>
        </MockThemeProvider>
      </ApolloProvider>
    );
  };

  return Wrapper;
};

describe('<Task />', () => {
  it('should render created task', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('Complete Task')).toBeInTheDocument();
  });

  it('should render created task with embedded form', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimedWithForm().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetForm', (_, res, ctx) => {
        return res.once(ctx.data(mockGetForm.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    expect(await screen.findByText('Complete Task')).toBeInTheDocument();
  });

  it('should render completed task', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskCompleted().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.queryByText('Complete Task')).not.toBeInTheDocument();
  });

  it('should render completed task with embedded form', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskCompletedWithForm().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetForm', (_, res, ctx) => {
        return res.once(ctx.data(mockGetForm.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    expect(screen.queryByText('Complete Task')).not.toBeInTheDocument();
  });

  it('should complete task without variables', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.mutation('CompleteTask', (_, res, ctx) => {
        return res.once(ctx.data(mockCompleteTask().result.data));
      }),
      graphql.query('GetTasks', (req, res, ctx) => {
        const {state, assigned} = req.variables;

        if (state === 'CREATED' && assigned === undefined) {
          return res(ctx.data(mockGetAllOpenTasks(true).result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid query',
            },
          ]),
        );
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables().result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    userEvent.click(await screen.findByText('Complete Task'));

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    });

    expect(mockDisplayNotification).toHaveBeenCalledWith('success', {
      headline: 'Task completed',
    });
  });

  it('should get error on complete task', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.mutation('CompleteTask', (_, res) => {
        return res.networkError('Network error');
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    userEvent.click(await screen.findByText('Complete Task'));

    await waitFor(() => {
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Task could not be completed',
        description: 'Service is not reachable',
      });
    });
  });

  it('should show a loading spinner while loading', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    expect(screen.getByTestId('details-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('details-overlay'));
  });

  it('should reset variables', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables().result.data));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnclaimTask.result.data));
      }),
      graphql.query('GetTasks', (req, res, ctx) => {
        const {state, assigned} = req.variables;

        if (state === 'CREATED' && assigned === undefined) {
          return res(ctx.data(mockGetAllOpenTasks(true).result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid query',
            },
          ]),
        );
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockClaimTask.result.data));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed('1').result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables('1').result.data));
      }),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    userEvent.click(await screen.findByText(/Add Variable/));
    userEvent.type(screen.getByLabelText('New variable 0 name'), 'valid_name');
    userEvent.type(
      screen.getByLabelText('New variable 0 value'),
      '"valid_value"',
    );

    expect(screen.getByLabelText('New variable 0 name')).toBeInTheDocument();
    expect(screen.getByLabelText('New variable 0 value')).toBeInTheDocument();

    userEvent.click(screen.getByText('Unclaim'));
    userEvent.click(await screen.findByText('Claim'));

    expect(await screen.findByText('Unclaim')).toBeInTheDocument();

    expect(screen.getByText(/Task has no Variables/)).toBeInTheDocument();
  });

  it('should render created task with variables form', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) =>
        res.once(ctx.data(mockGetCurrentUser.result.data)),
      ),
      graphql.query('GetTask', (_, res, ctx) =>
        res.once(ctx.data(mockGetTaskClaimedWithForm().result.data)),
      ),
      graphql.query('GetForm', (_, res, ctx) =>
        res.once(ctx.data(mockGetInvalidForm.result.data)),
      ),
      graphql.query('GetTaskVariables', (_, res, ctx) =>
        res.once(ctx.data(mockGetTaskVariables().result.data)),
      ),
    );

    render(<Task />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Invalid Form schema',
    });
  });
});
