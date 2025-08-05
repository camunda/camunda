/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from './index';
import {Component as LayoutComponent} from 'v2/TaskDetailsLayout';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'common/testing/testing-library';
import {http, HttpResponse} from 'msw';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {LocationLog} from 'common/testing/LocationLog';
import {notificationsStore} from 'common/notifications/notifications.store';
import * as formMocks from 'v2/mocks/form';
import * as variableMocks from 'v2/mocks/variables';
import * as taskMocks from 'v2/mocks/task';
import * as tasksMocks from 'v2/mocks/tasks';
import * as userMocks from 'common/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';

const {getQueryVariablesResponseMock} = variableMocks;
const {getQueryTasksResponseMock} = tasksMocks;

const MOCK_USER_TASK_KEY = '0';

vi.mock('common/notifications/notifications.store', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

vi.mock('modules/stores/autoSelectFirstTask', () => ({
  autoSelectNextTaskStore: {
    enabled: false,
  },
}));

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [`/${MOCK_USER_TASK_KEY}`],
) => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path=":id" Component={LayoutComponent}>
              <Route index element={children} />
            </Route>
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<Task />', () => {
  it('should render created task', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => {
          return HttpResponse.json(
            taskMocks.assignedTask({
              userTaskKey: MOCK_USER_TASK_KEY,
              formKey: undefined,
            }),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/variables/search',
        () => {
          return HttpResponse.json(
            getQueryVariablesResponseMock(variableMocks.variables),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        async () => {
          return HttpResponse.json(getQueryTasksResponseMock(tasksMocks.tasks));
        },
        {once: true},
      ),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('Complete Task')).toBeInTheDocument();
  });

  it('should render completed task', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => {
          return HttpResponse.json(
            taskMocks.completedTask({
              userTaskKey: MOCK_USER_TASK_KEY,
              formKey: undefined,
            }),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/variables/search',
        () => {
          return HttpResponse.json(
            getQueryVariablesResponseMock(variableMocks.variables),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        async () => {
          return HttpResponse.json(getQueryTasksResponseMock(tasksMocks.tasks));
        },
        {once: true},
      ),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(await screen.findByText('Variables')).toBeInTheDocument();

    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should render completed task with form', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => {
          return HttpResponse.json(
            taskMocks.completedTask({
              userTaskKey: MOCK_USER_TASK_KEY,
            }),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v2/user-tasks/:userTaskKey/form',
        () => {
          return HttpResponse.json(formMocks.form);
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/variables/search',
        () => {
          return HttpResponse.json(
            getQueryVariablesResponseMock(variableMocks.variables),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        async () => {
          return HttpResponse.json(getQueryTasksResponseMock(tasksMocks.tasks));
        },
        {once: true},
      ),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should complete task without variables', async () => {
    let taskState: 'CREATED' | 'COMPLETED' = 'CREATED';

    nodeMockServer.use(
      http.get('/v2/user-tasks/:userTaskKey', () => {
        return HttpResponse.json(
          taskMocks.assignedTask({
            userTaskKey: MOCK_USER_TASK_KEY,
            formKey: undefined,
            state: taskState,
          }),
        );
      }),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/completion',
        () => {
          taskState = 'COMPLETED';
          return HttpResponse.json();
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/variables/search',
        () => {
          return HttpResponse.json(getQueryVariablesResponseMock([]));
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        () => {
          return HttpResponse.json(getQueryTasksResponseMock([]));
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        async () => {
          return HttpResponse.json(
            getQueryTasksResponseMock(tasksMocks.unassignedTasks),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => {
          return HttpResponse.json(
            taskMocks.completedTask({
              userTaskKey: MOCK_USER_TASK_KEY,
              formKey: undefined,
            }),
          );
        },
        {once: true},
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /complete task/i})).toBeEnabled();

    await user.click(screen.getByRole('button', {name: /complete task/i}));

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
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => {
          return HttpResponse.json(
            taskMocks.assignedTask({
              userTaskKey: MOCK_USER_TASK_KEY,
              formKey: undefined,
            }),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/completion',
        () => {
          return HttpResponse.error();
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/variables/search',
        () => {
          return HttpResponse.json(
            getQueryVariablesResponseMock(variableMocks.variables),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        async () => {
          return HttpResponse.json(
            getQueryTasksResponseMock(tasksMocks.unassignedTasks),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(),
    });

    await user.click(
      await screen.findByRole('button', {name: /complete task/i}),
    );

    expect(await screen.findByText('Completion failed')).toBeInTheDocument();

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Task could not be completed',
        isDismissable: true,
      });
    });

    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
  });

  it('should show a skeleton while loading', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => {
          return HttpResponse.json(
            taskMocks.assignedTask({
              userTaskKey: MOCK_USER_TASK_KEY,
              formKey: undefined,
            }),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/variables/search',
        () => {
          return HttpResponse.json(
            getQueryVariablesResponseMock(variableMocks.variables),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        () => {
          return HttpResponse.json(
            getQueryTasksResponseMock(tasksMocks.unassignedTasks),
          );
        },
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/search',
        async () => {
          return HttpResponse.json(
            getQueryTasksResponseMock(tasksMocks.unassignedTasks),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
  });

  it('should reset variables', async () => {
    nodeMockServer.use(
      http.get('/v2/user-tasks/:userTaskKey', () => {
        return HttpResponse.json(
          taskMocks.assignedTask({
            userTaskKey: MOCK_USER_TASK_KEY,
            formKey: undefined,
          }),
        );
      }),
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post('/v2/user-tasks/:userTaskKey/variables/search', () => {
        return HttpResponse.json([]);
      }),
      http.delete('/v2/user-tasks/:userTaskKey/assignee', () => {
        return HttpResponse.json();
      }),
      http.post('/v2/user-tasks/:userTaskKey/assignment', () => {
        return HttpResponse.json();
      }),
      http.post('/v2/user-tasks/search', async () => {
        return HttpResponse.json(
          getQueryTasksResponseMock(tasksMocks.unassignedTasks),
        );
      }),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(),
    });

    await user.click(
      await screen.findByRole('button', {name: /add variable/i}),
    );
    await user.type(screen.getByLabelText(/1st variable name/i), 'valid_name');
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );

    nodeMockServer.use(
      http.get('/v2/user-tasks/:userTaskKey', () => {
        return HttpResponse.json(
          taskMocks.unassignedTask({
            userTaskKey: MOCK_USER_TASK_KEY,
            formKey: undefined,
          }),
        );
      }),
    );

    await user.click(screen.getByRole('button', {name: /^unassign$/i}));

    nodeMockServer.use(
      http.get('/v2/user-tasks/:userTaskKey', () => {
        return HttpResponse.json(
          taskMocks.assignedTask({
            userTaskKey: MOCK_USER_TASK_KEY,
            formKey: undefined,
          }),
        );
      }),
    );

    await user.click(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText(/task has no variables/i)).toBeInTheDocument();
  });

  it('should render variables view with invalid form schema', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => {
          return HttpResponse.json(
            taskMocks.assignedTaskWithForm({
              userTaskKey: MOCK_USER_TASK_KEY,
            }),
          );
        },
        {once: true},
      ),
      http.get(
        '/v2/user-tasks/:userTaskKey/form',
        () => HttpResponse.json(formMocks.invalidForm),
        {once: true},
      ),
      http.post(
        '/v2/user-tasks/:userTaskKey/variables/search',
        () => {
          return HttpResponse.json(
            getQueryVariablesResponseMock(variableMocks.variables),
          );
        },
        {once: true},
      ),
      http.post('/v2/user-tasks/search', () => {
        return HttpResponse.json(
          getQueryTasksResponseMock(tasksMocks.unassignedTasks),
        );
      }),
      http.get(
        '/v2/process-definitions/:processDefinitionKey/xml',
        async () => new HttpResponse(undefined, {status: 404}),
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
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
