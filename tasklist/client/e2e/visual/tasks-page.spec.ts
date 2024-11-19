/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Route, type Request} from '@playwright/test';
import schema from '@/resources/bigForm.json' assert {type: 'json'};
import {test} from '@/visual-fixtures';
import {MOCK_TENANTS} from '@/mocks/tenants';
import {multiTenancyUser} from '@/mocks/users';
import {clientConfig} from '@/mocks/clientConfig';
import {bpmnProcessXml} from '@/mocks/bpmn';
import {
  getQueryTasksResponseMock,
  getTask,
  taskWithForm,
  taskWithoutForm,
  type Task,
} from '@/mocks/tasks';
import {apiURLPattern} from '@/mocks/apiURLPattern';
import {variables} from '@/mocks/variables';
import {
  endpoints,
  type UserTask,
} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {invalidLicense} from '@/mocks/licenses';

function mockResponses(
  tasks: Array<UserTask> = [],
  task: Task = taskWithoutForm,
  variables: unknown[] = [],
  bpmnXml: string | undefined = bpmnProcessXml,
): (router: Route, request: Request) => Promise<unknown> | unknown {
  return (route) => {
    if (
      route.request().url().includes(`v1/tasks/${task.id}/variables/search`)
    ) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(variables),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes(endpoints.queryUserTasks.getUrl())) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(getQueryTasksResponseMock(tasks)),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes(`v1/tasks/${task.id}`)) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(task),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    const formId = (task.formKey ?? '').replace('camunda-forms:bpmn:', '');

    if (route.request().url().includes(`v1/forms/${formId}`)) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: formId,
          processDefinitionKey: '2251799813685255',
          schema: JSON.stringify(schema),
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('v1/internal/users/current')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(multiTenancyUser),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route
        .request()
        .url()
        .includes(`v1/internal/processes/${task.processDefinitionKey}`)
    ) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: task.processDefinitionKey,
          name: 'A test process',
          bpmnProcessId: 'someProcessId',
          version: 1,
          startEventFormId: '123456789',
          sortValues: ['value'],
          bpmnXml,
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('v2/license')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(invalidLicense),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    return route.continue();
  };
}

test.describe('tasks page', () => {
  test('empty state', async ({page, tasksPage}) => {
    await page.route(apiURLPattern, mockResponses());

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({page, tasksPage}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('theme', '"dark"');
    })()`);
    await page.route(apiURLPattern, mockResponses());

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state when completed task before', async ({page, tasksPage}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasCompletedTask', 'true');
    })()`);
    await page.route(apiURLPattern, mockResponses());

    await tasksPage.goto();

    await expect(page.getByText('No tasks found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty list', async ({page, tasksPage}) => {
    await page.route(apiURLPattern, mockResponses());

    await tasksPage.goto({filter: 'completed', sortBy: 'creation'});

    await expect(page).toHaveScreenshot();
  });

  test('all open tasks', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses([
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          assignee: 'jane',
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
        }),
      ]),
    );

    await tasksPage.goto();

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tasks assigned to me', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses([
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          assignee: 'demo',
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          assignee: 'demo',
        }),
      ]),
    );

    await tasksPage.goto({filter: 'assigned-to-me', sortBy: 'follow-up'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('unassigned tasks', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses([
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
        }),
      ]),
    );

    await tasksPage.goto({filter: 'unassigned', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();
  });

  test('completed tasks', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses([
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          completionDate: '2025-04-17T16:57:41.000Z',
          assignee: 'demo',
          state: 'COMPLETED',
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          completionDate: '2025-04-17T16:57:41.000Z',
          assignee: 'jane',
          state: 'COMPLETED',
        }),
      ]),
    );

    await tasksPage.goto({filter: 'completed', sortBy: 'completion'});
    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle(/Created on.*/).hover();
    await expect(page).toHaveScreenshot();
    await task.getByTitle(/Completed on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by due date', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses([
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          assignee: 'jane',
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
        }),
      ]),
    );

    await tasksPage.goto({filter: 'all-open', sortBy: 'due'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle(/Due on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by follow up date', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses([
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          assignee: 'jane',
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
        }),
      ]),
    );

    await tasksPage.goto({filter: 'all-open', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle(/Follow-up on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by priority', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses([
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          assignee: 'jane',
          priority: 76,
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          priority: 51,
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          priority: 26,
        }),
        getTask({
          elementName: 'Register the passenger',
          processName: 'Flight registration',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          priority: 1,
        }),
      ]),
    );

    await tasksPage.goto({sortBy: 'priority'});

    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle('Critical').hover();

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and without variables', async ({
    page,
    tasksPage,
  }) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            creationDate: '2025-04-13T16:57:41.482+0000',
            userTaskKey: Number(taskWithoutForm.id),
          }),
        ],
        taskWithoutForm,
      ),
    );

    await tasksPage.gotoTaskDetails(taskWithoutForm.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and with variables', async ({
    page,
    taskVariableView,
    tasksPage,
  }) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            creationDate: '2025-04-13T16:57:41.482+0000',
            assignee: 'demo',
            userTaskKey: Number(taskWithoutForm.id),
          }),
        ],
        {
          ...taskWithoutForm,
          assignee: 'demo',
        },
        variables,
      ),
    );

    await tasksPage.gotoTaskDetails(taskWithoutForm.id);

    await expect(page).toHaveScreenshot();

    await taskVariableView.addVariable({name: 'var', value: '"lorem ipsum"'});
    await expect(page.getByText('Complete task')).toBeEnabled();

    await expect(page).toHaveScreenshot();

    await page.getByLabel('Open JSON code editor').nth(0).hover();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            creationDate: '2025-04-13T16:57:41.482+0000',
            assignee: 'demo',
            userTaskKey: Number(taskWithoutForm.id),
          }),
        ],
        {
          ...taskWithoutForm,
          assignee: 'demo',
        },
      ),
    );

    await tasksPage.gotoTaskDetails(taskWithoutForm.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            creationDate: '2025-04-13T16:57:41.482+0000',
            assignee: 'demo',
            userTaskKey: Number(taskWithoutForm.id),
            state: 'COMPLETED',
            completionDate: '2025-04-18T16:57:41.000Z',
          }),
        ],
        {
          ...taskWithoutForm,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2025-04-18T16:57:41.000Z',
        },
      ),
    );

    await tasksPage.gotoTaskDetails(taskWithoutForm.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task with variables', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            creationDate: '2025-04-13T16:57:41.482+0000',
            assignee: 'demo',
            userTaskKey: Number(taskWithoutForm.id),
            state: 'COMPLETED',
            completionDate: '2025-04-18T16:57:41.000Z',
          }),
        ],
        {
          ...taskWithoutForm,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2025-04-18T16:57:41.000Z',
        },
        variables,
      ),
    );

    await tasksPage.gotoTaskDetails(taskWithoutForm.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected unassigned task with form', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Big form task',
            processName: 'Big form process',
            creationDate: '2025-04-13T16:57:41.482+0000',
            userTaskKey: Number(taskWithForm.id),
          }),
        ],
        {
          ...taskWithForm,
          assignee: null,
        },
      ),
    );

    await tasksPage.gotoTaskDetails(taskWithForm.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task with form', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Big form task',
            processName: 'Big form process',
            creationDate: '2025-04-13T16:57:41.482+0000',
            userTaskKey: Number(taskWithForm.id),
            assignee: 'demo',
          }),
        ],
        {
          ...taskWithForm,
          assignee: 'demo',
        },
      ),
    );

    await tasksPage.gotoTaskDetails(taskWithForm.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tenant on task detail', async ({page, tasksPage}) => {
    const NON_FORM_TASK_WITH_TENANT = {
      ...taskWithoutForm,
      tenantId: MOCK_TENANTS[0].id,
    };
    const V2_TASK_WITH_TENANT = getTask({
      tenantId: MOCK_TENANTS[0].id,
      elementName: taskWithoutForm.name,
      processName: taskWithoutForm.processName,
      userTaskKey: Number(taskWithoutForm.id),
    });

    await page.route('**/client-config.js', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/javascript;charset=UTF-8',
        },
        body: `window.clientConfig = ${JSON.stringify(clientConfig)};`,
      }),
    );
    await page.route(
      apiURLPattern,
      mockResponses([V2_TASK_WITH_TENANT], NON_FORM_TASK_WITH_TENANT),
    );

    await tasksPage.gotoTaskDetails(NON_FORM_TASK_WITH_TENANT.id);

    await expect(page).toHaveScreenshot();
  });

  test('expanded side panel', async ({page, tasksPage}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem(
        'customFilters',
        JSON.stringify({
          custom: {
            status: 'completed',
            assignee: 'all',
            bpmnProcess: 'process-1',
          },
        }),
      );
    })()`);

    await page.route(apiURLPattern, mockResponses());

    await tasksPage.goto();

    await tasksPage.expandSidePanelButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('custom filters modal', async ({page, tasksPage}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem(
        'customFilters',
        JSON.stringify({
          custom: {
            status: 'completed',
            assignee: 'all',
            bpmnProcess: 'process-1',
          },
        }),
      );
    })()`);

    await page.route(apiURLPattern, mockResponses());

    await tasksPage.goto();

    await tasksPage.expandSidePanelButton.click();
    await tasksPage.addCustomFilterButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('process view', async ({page, tasksPage}) => {
    await page.route(
      apiURLPattern,
      mockResponses(
        [
          getTask({
            elementName: 'Big form task',
            processName: 'Big form process',
            creationDate: '2025-04-13T16:57:41.482+0000',
            userTaskKey: Number(taskWithForm.id),
            assignee: 'demo',
          }),
        ],
        taskWithForm,
      ),
    );

    await tasksPage.gotoTaskDetailsProcessTab(taskWithForm.id);

    await expect(page).toHaveScreenshot();
  });
});
