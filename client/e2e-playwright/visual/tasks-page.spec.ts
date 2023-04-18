/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect, Route, Request} from '@playwright/test';
import schema from '../bigForm.json';

const NON_FORM_TASK = {
  id: '2251799813687061',
  formKey: null,
  processDefinitionId: '2251799813685281',
  assignee: null,
  name: 'Activity_1ygafd4',
  taskState: 'CREATED',
  processName: 'TwoUserTasks',
  creationTime: '2023-04-13T16:57:41.482+0000',
  completionTime: null,
  candidateGroups: ['demo group'],
  candidateUsers: ['demo'],
  followUpDate: '2023-04-19T16:57:41.000Z',
  dueDate: '2023-04-18T16:57:41.000Z',
  __typename: 'Task',
} as const;

const NON_FORM_TASK_EMPTY_VARIABLES = {
  id: NON_FORM_TASK.id,
  variables: [],
  __typename: 'Task',
} as const;

const NON_FORM_TASK_VARIABLES = {
  id: NON_FORM_TASK.id,
  variables: [
    {
      id: '2251799813686711-small',
      name: 'small',
      previewValue: '"Hello World"',
      isValueTruncated: false,
      __typename: 'Variable',
    },
  ],
  __typename: 'Task',
} as const;

const FORM_TASK = {
  id: '2251799813687045',
  formKey: 'camunda-forms:bpmn:userTaskForm_3j0n396',
  processDefinitionId: '2251799813685277',
  assignee: null,
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationTime: '2023-04-13T16:57:41.475+0000',
  completionTime: null,
  candidateGroups: null,
  candidateUsers: null,
  dueDate: null,
  followUpDate: null,
  __typename: 'Task',
} as const;

function mockResponses(
  tasks: Array<unknown> = [],
  task: unknown = NON_FORM_TASK,
  variables: unknown = NON_FORM_TASK_EMPTY_VARIABLES,
): (router: Route, request: Request) => Promise<unknown> | unknown {
  return (route) => {
    const {operationName} = route.request().postDataJSON();

    switch (operationName) {
      case 'GetCurrentUser':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              currentUser: {
                userId: 'demo',
                displayName: 'demo',
                permissions: ['READ', 'WRITE'],
                salesPlanType: null,
                roles: null,
                c8Links: [],
                __typename: 'User',
              },
            },
          }),
        });
      case 'GetTasks':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              tasks,
            },
          }),
        });
      case 'GetTask':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              task,
            },
          }),
        });
      case 'GetTaskVariables':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              task: variables,
            },
          }),
        });
      case 'GetSelectedVariables':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              variables: [],
            },
          }),
        });
      case 'GetForm':
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              form: {
                schema: JSON.stringify(schema),
              },
            },
          }),
        });
      default:
        return route.fulfill({
          status: 500,
          body: JSON.stringify({
            message: '',
          }),
        });
    }
  };
}

test.describe('tasks page', () => {
  test('empty state', async ({page}) => {
    await page.route('**/graphql', mockResponses());

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('theme', '"dark"');
    });
    await page.route('**/graphql', mockResponses());

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state when completed task before', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasCompletedTask', 'true');
    });
    await page.route('**/graphql', mockResponses());

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty list', async ({page}) => {
    await page.route('**/graphql', mockResponses());

    await page.goto('/?filter=completed&sortBy=creation', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('all open tasks', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationTime: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          __typename: 'Task',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationTime: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          __typename: 'Task',
        },
      ]),
    );

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('tasks assigned to me', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationTime: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          __typename: 'Task',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationTime: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          __typename: 'Task',
        },
      ]),
    );

    await page.goto('/?filter=assigned-to-me&sortBy=follow-up', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('unassigned tasks', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationTime: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          __typename: 'Task',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationTime: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          __typename: 'Task',
        },
      ]),
    );

    await page.goto('/?filter=unassigned&sortBy=follow-up', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('completed tasks', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationTime: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          __typename: 'Task',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationTime: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          __typename: 'Task',
        },
      ]),
    );

    await page.goto('/?filter=completed&sortBy=follow-up', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by due date', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationTime: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          __typename: 'Task',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationTime: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          __typename: 'Task',
        },
      ]),
    );

    await page.goto('/?filter=all-open&sortBy=due', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by follow up date', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationTime: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          __typename: 'Task',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationTime: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          __typename: 'Task',
        },
      ]),
    );

    await page.goto('/?filter=all-open&sortBy=follow-up', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and without variables', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: null,
            creationTime: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            __typename: 'Task',
          },
        ],
        NON_FORM_TASK,
      ),
    );

    await page.goto(`/${NON_FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and with variables', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationTime: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            __typename: 'Task',
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
        },
        NON_FORM_TASK_VARIABLES,
      ),
    );

    await page.goto(`/${NON_FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationTime: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            __typename: 'Task',
          },
        ],
        NON_FORM_TASK,
      ),
    );

    await page.goto(`/${NON_FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationTime: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'COMPLETED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            __typename: 'Task',
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionTime: '2023-04-18T16:57:41.000Z',
        },
      ),
    );

    await page.goto(`/${NON_FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task with variables', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationTime: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'COMPLETED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            __typename: 'Task',
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionTime: '2023-04-18T16:57:41.000Z',
        },
        NON_FORM_TASK_VARIABLES,
      ),
    );

    await page.goto(`/${NON_FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected unassigned task with form', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: null,
            creationTime: '2023-04-13T16:57:41.475+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061475', '2251799813687045'],
            isFirst: false,
            __typename: 'Task',
          },
        ],
        FORM_TASK,
      ),
    );

    await page.goto(`/${FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task with form', async ({page}) => {
    await page.route(
      '**/graphql',
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: 'demo',
            creationTime: '2023-04-13T16:57:41.475+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061475', '2251799813687045'],
            isFirst: false,
            __typename: 'Task',
          },
        ],
        {
          ...FORM_TASK,
          assignee: 'demo',
        },
      ),
    );

    await page.goto(`/${FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
