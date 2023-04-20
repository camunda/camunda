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
} from 'modules/testing-library';
import {
  mockGetTaskAssigned,
  mockGetTaskCompletedWithForm,
  mockGetTaskAssignedWithForm,
  mockGetTaskCompleted,
} from 'modules/queries/get-task';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {mockCompleteTask} from 'modules/mutations/complete-task';
import {mockAssignTask} from 'modules/mutations/assign-task';
import {mockUnassignTask} from 'modules/mutations/unassign-task';
import {mockGetForm, mockGetInvalidForm} from 'modules/queries/get-form';
import {
  mockGetTaskVariables,
  mockGetTaskEmptyVariables,
} from 'modules/queries/get-task-variables';
import {mockGetSelectedVariables} from 'modules/queries/get-selected-variables';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {graphql} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {LocationLog} from 'modules/utils/LocationLog';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
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
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it('should render created task', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskAssigned().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('Complete Task')).toBeInTheDocument();
  });

  it('should render created task with embedded form', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskAssignedWithForm().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetForm', (_, res, ctx) => {
        return res.once(ctx.data(mockGetForm.result.data));
      }),
      graphql.query('GetSelectedVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetSelectedVariables().result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
    expect(screen.getByTestId('embedded-form')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
  });

  it('should render completed task', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskCompleted().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(await screen.findByText('Variables')).toBeInTheDocument();

    expect(screen.queryByText('Complete Task')).not.toBeVisible();
  });

  it('should render completed task with embedded form', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskCompletedWithForm().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetForm', (_, res, ctx) => {
        return res.once(ctx.data(mockGetForm.result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    expect(screen.queryByText('Complete Task')).not.toBeVisible();
  });

  it('should complete task without variables', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskAssigned().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.mutation('CompleteTask', (_, res, ctx) => {
        return res.once(ctx.data(mockCompleteTask().result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables().result.data));
      }),
    );

    const {user} = render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await user.click(
      await screen.findByRole('button', {name: /complete task/i}),
    );

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'success',
      title: 'Task completed',
      isDismissable: true,
    });
  });

  it('should get error on complete task', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskAssigned().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.mutation('CompleteTask', (_, res) => {
        return res.networkError('Network error');
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    const {user} = render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await user.click(
      await screen.findByRole('button', {name: /complete task/i}),
    );

    expect(screen.getByText('Completing task...')).toBeInTheDocument();
    expect(await screen.findByText('Completion failed')).toBeInTheDocument();

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Task could not be completed',
        subtitle: 'Service is not reachable',
        isDismissable: true,
      });
    });

    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
  });

  it('should show a skeleton while loading', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskAssigned().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskVariables().result.data));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
  });

  it('should reset variables', async () => {
    nodeMockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskAssigned().result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables().result.data));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnassignTask.result.data));
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockAssignTask.result.data));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskAssigned().result.data));
      }),
      graphql.query('GetTaskVariables', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskEmptyVariables().result.data));
      }),
    );

    const {user} = render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await user.click(
      await screen.findByRole('button', {name: /add variable/i}),
    );
    await user.type(screen.getByLabelText(/1st variable name/i), 'valid_name');
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );
    await user.click(screen.getByRole('button', {name: /^unassign$/i}));
    await user.click(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText(/task has no variables/i)).toBeInTheDocument();
  });

  it('should render created task with variables form', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) =>
        res.once(ctx.data(mockGetCurrentUser)),
      ),
      graphql.query('GetTask', (_, res, ctx) =>
        res.once(ctx.data(mockGetTaskAssignedWithForm().result.data)),
      ),
      graphql.query('GetForm', (_, res, ctx) =>
        res.once(ctx.data(mockGetInvalidForm.result.data)),
      ),
      graphql.query('GetTaskVariables', (_, res, ctx) =>
        res.once(ctx.data(mockGetTaskVariables().result.data)),
      ),
      graphql.query('GetTaskVariables', (_, res, ctx) =>
        res.once(ctx.data(mockGetTaskVariables().result.data)),
      ),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Invalid Form schema',
      isDismissable: true,
    });
  });
});
