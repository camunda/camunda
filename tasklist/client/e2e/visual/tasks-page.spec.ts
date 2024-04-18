/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {expect, Route, Request} from '@playwright/test';
import schema from '../resources/bigForm.json' assert {type: 'json'};
import {test} from '../test-fixtures';

const MOCK_TENANTS = [
  {
    id: 'tenantA',
    name: 'Tenant A',
  },
  {
    id: 'tenantB',
    name: 'Tenant B',
  },
];

const NON_FORM_TASK = {
  id: '2251799813687061',
  formKey: null,
  formId: null,
  formVersion: null,
  isFormEmbedded: null,
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
  tenantId: null,
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
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
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
  tenantId: null,
};

function mockResponses(
  tasks: Array<unknown> = [],
  task = NON_FORM_TASK,
  variables: unknown[] = NON_FORM_TASK_EMPTY_VARIABLES,
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
          tenants: MOCK_TENANTS,
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
  test('empty state', async ({page, taskPanelPage}) => {
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await taskPanelPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({page, taskPanelPage}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('theme', '"dark"');
    });
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await taskPanelPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state when completed task before', async ({
    page,
    taskPanelPage,
  }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('hasCompletedTask', 'true');
    });
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await taskPanelPage.goto();

    await expect(page.getByText('No tasks found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty list', async ({page, taskPanelPage}) => {
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await taskPanelPage.goto({filter: 'completed', sortBy: 'creation'});

    await expect(page).toHaveScreenshot();
  });

  test('all open tasks', async ({page, taskPanelPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
      ]),
    );

    await taskPanelPage.goto();

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tasks assigned to me', async ({page, taskPanelPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
      ]),
    );

    await taskPanelPage.goto({filter: 'assigned-to-me', sortBy: 'follow-up'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('unassigned tasks', async ({page, taskPanelPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
      ]),
    );

    await taskPanelPage.goto({filter: 'unassigned', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();
  });

  test('completed tasks', async ({page, taskPanelPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: '2025-04-17T16:57:41.000Z',
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: '2025-04-17T16:57:41.000Z',
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
      ]),
    );

    await taskPanelPage.goto({filter: 'completed', sortBy: 'completion'});
    await expect(page).toHaveScreenshot();

    const task = taskPanelPage.task('Register the passenger');
    await task.getByTitle(/Created on.*/).hover();
    await expect(page).toHaveScreenshot();
    await task.getByTitle(/Completed on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by due date', async ({page, taskPanelPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
      ]),
    );

    await taskPanelPage.goto({filter: 'all-open', sortBy: 'due'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();

    const task = taskPanelPage.task('Register the passenger');
    await task.getByTitle(/Due on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by follow up date', async ({page, taskPanelPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2025-04-13T16:57:41.025+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2025-04-13T16:57:41.067+0000',
          followUpDate: '2025-04-19T16:57:41.000Z',
          dueDate: '2025-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
        },
      ]),
    );

    await taskPanelPage.goto({filter: 'all-open', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();

    const task = taskPanelPage.task('Register the passenger');
    await task.getByTitle(/Follow-up on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and without variables', async ({
    page,
    taskDetailsPage,
  }) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: null,
            creationDate: '2025-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            formId: null,
            formVersion: null,
            isFormEmbedded: true,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
          },
        ],
        NON_FORM_TASK,
      ),
    );

    await taskDetailsPage.goto(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and with variables', async ({
    page,
    taskDetailsPage,
  }) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2025-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            formId: null,
            formVersion: null,
            isFormEmbedded: true,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
        },
        NON_FORM_TASK_VARIABLES,
      ),
    );

    await taskDetailsPage.goto(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task', async ({page, taskDetailsPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2025-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            formId: null,
            formVersion: null,
            isFormEmbedded: true,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
        },
      ),
    );

    await taskDetailsPage.goto(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task', async ({page, taskDetailsPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2025-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'COMPLETED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            formId: null,
            formVersion: null,
            isFormEmbedded: true,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2025-04-18T16:57:41.000Z',
        },
      ),
    );

    await taskDetailsPage.goto(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task with variables', async ({
    page,
    taskDetailsPage,
  }) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2025-04-13T16:57:41.482+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'COMPLETED',
            sortValues: ['1681405061482', '2251799813687061'],
            isFirst: false,
            formKey: null,
            formId: null,
            formVersion: null,
            isFormEmbedded: true,
            processDefinitionKey: '2251799813685259',
            taskDefinitionId: 'Activity_1ygafd4',
            processInstanceKey: '4503599627371080',
            completionDate: null,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2025-04-18T16:57:41.000Z',
        },
        NON_FORM_TASK_VARIABLES,
      ),
    );

    await taskDetailsPage.goto(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected unassigned task with form', async ({
    page,
    taskDetailsPage,
  }) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: null,
            creationDate: '2025-04-13T16:57:41.475+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061475', '2251799813687045'],
            isFirst: false,
            formKey: 'camunda-forms:bpmn:userTaskForm_1',
            formId: null,
            formVersion: null,
            isFormEmbedded: true,
            processDefinitionKey: '2251799813685255',
            completionDate: null,
            taskDefinitionId: 'Activity_0aecztp',
            processInstanceKey: '4503599627371425',
            candidateGroups: null,
            candidateUsers: null,
            context: null,
          },
        ],
        {
          ...FORM_TASK,
          assignee: null,
        },
      ),
    );

    await taskDetailsPage.goto(FORM_TASK.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task with form', async ({page, taskDetailsPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: 'demo',
            creationDate: '2025-04-13T16:57:41.475+0000',
            followUpDate: null,
            dueDate: null,
            taskState: 'CREATED',
            sortValues: ['1681405061475', '2251799813687045'],
            isFirst: false,
            formKey: 'camunda-forms:bpmn:userTaskForm_1',
            formId: null,
            formVersion: null,
            isFormEmbedded: true,
            processDefinitionKey: '2251799813685255',
            completionDate: null,
            taskDefinitionId: 'Activity_0aecztp',
            processInstanceKey: '4503599627371425',
            candidateGroups: null,
            candidateUsers: null,
            context: null,
          },
        ],
        {
          ...FORM_TASK,
          assignee: 'demo',
        },
      ),
    );

    await taskDetailsPage.goto(FORM_TASK.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tenant on task detail', async ({page, taskDetailsPage}) => {
    const NON_FORM_TASK_WITH_TENANT = {
      ...NON_FORM_TASK,
      tenantId: MOCK_TENANTS[0].id,
      context: null,
    };

    await page.route('**/client-config.js', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/javascript;charset=UTF-8',
        },
        body: `window.clientConfig = {
        "isEnterprise":false,
        "canLogout":true,
        "isLoginDelegated":false,
        "contextPath":"",
        "organizationId":null,
        "clusterId":null,
        "stage":null,
        "mixpanelToken":null,
        "mixpanelAPIHost":null,
        "isMultiTenancyEnabled": true
      };`,
      }),
    );
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([NON_FORM_TASK_WITH_TENANT], NON_FORM_TASK_WITH_TENANT),
    );

    await taskDetailsPage.goto(NON_FORM_TASK_WITH_TENANT.id);

    await expect(page).toHaveScreenshot();
  });
});
