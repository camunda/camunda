/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect, Route, Request} from '@playwright/test';
import schema from '../resources/bigForm.json';

const NON_FORM_TASK = {
  id: '2251799813687061',
  formKey: null,
  processDefinitionKey: '2251799813685259',
  taskDefinitionId: 'Activity_1ygafd4',
  processInstanceKey: '4503599627371080',
  assignee: null,
  name: 'Activity_1ygafd4',
  taskState: 'CREATED',
  processName: 'TwoUserTasks',
  creationDate: '2023-04-13T16:57:41.482+0000',
  completionDate: null,
  candidateGroups: ['demo group'],
  candidateUsers: ['demo'],
  followUpDate: null,
  dueDate: null,
  sortValues: ['1684881752515', '4503599627371089'],
  isFirst: true,
};

const NON_FORM_TASK_EMPTY_VARIABLES = [];

const NON_FORM_TASK_VARIABLES = [
  {
    id: '2251799813686711-small',
    name: 'small',
    previewValue: '"Hello World"',
    value: '"Hello World"',
    isValueTruncated: false,
  },
];

const FORM_TASK = {
  id: '2251799813687045',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  processDefinitionKey: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
};

function mockResponses(
  tasks: Array<unknown> = [],
  task: any = NON_FORM_TASK,
  variables: any[] = NON_FORM_TASK_EMPTY_VARIABLES,
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

    if (route.request().url().includes('v1/tasks/search')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(tasks),
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
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          permissions: ['READ', 'WRITE'],
          salesPlanType: null,
          roles: null,
          c8Links: [],
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

test.describe('tasks page', () => {
  test('empty state', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('theme', '"dark"');
    });
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('empty state when completed task before', async ({page}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasCompletedTask', 'true');
    });
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page.getByText('No tasks found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty list', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await page.goto('/?filter=completed&sortBy=creation', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('all open tasks', async ({page}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
      ]),
    );

    await page.goto('/', {
      waitUntil: 'networkidle',
    });

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tasks assigned to me', async ({page}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
      ]),
    );

    await page.goto('/?filter=assigned-to-me&sortBy=follow-up', {
      waitUntil: 'networkidle',
    });

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('unassigned tasks', async ({page}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
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
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
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
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
      ]),
    );

    await page.goto('/?filter=all-open&sortBy=due', {
      waitUntil: 'networkidle',
    });

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by follow up date', async ({page}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2023-04-13T16:57:41.025+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2023-04-13T16:57:41.067+0000',
          followUpDate: '2023-04-19T16:57:41.000Z',
          dueDate: '2023-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
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
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: null,
            creationDate: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
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
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
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
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
        },
      ),
    );

    await page.goto(`/${NON_FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task', async ({page}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'COMPLETED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2023-04-18T16:57:41.000Z',
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
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2023-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'COMPLETED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2023-04-18T16:57:41.000Z',
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
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: null,
            creationDate: '2023-04-13T16:57:41.475+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061475', '2251799813687045'],
            isFirst: false,
            formKey: 'camunda-forms:bpmn:userTaskForm_1',
            processDefinitionKey: '2251799813685255',
            completionDate: null,
            taskDefinitionId: 'Activity_0aecztp',
            processInstanceKey: '4503599627371425',
            candidateGroups: null,
            candidateUsers: null,
          },
        ],
        {
          ...FORM_TASK,
          assignee: null,
        },
      ),
    );

    await page.goto(`/${FORM_TASK.id}`, {
      waitUntil: 'networkidle',
    });

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task with form', async ({page}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: 'demo',
            creationDate: '2023-04-13T16:57:41.475+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061475', '2251799813687045'],
            isFirst: false,
            formKey: 'camunda-forms:bpmn:userTaskForm_1',
            processDefinitionKey: '2251799813685255',
            completionDate: null,
            taskDefinitionId: 'Activity_0aecztp',
            processInstanceKey: '4503599627371425',
            candidateGroups: null,
            candidateUsers: null,
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

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});
