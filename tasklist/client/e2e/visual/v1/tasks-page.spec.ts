/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test, MOCK_TENANTS} from '@/fixtures/v1-visual';
import {nonFormTask, formTask} from '@/mocks/v1/task';
import {variables} from '@/mocks/v1/variables';

test.describe('tasks page', () => {
  test('empty state', async ({page, tasksPage, mockGetTasksRequest}) => {
    mockGetTasksRequest();

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('theme', '"dark"');
    })()`);
    mockGetTasksRequest();

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state when completed task before', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasCompletedTask', 'true');
    })()`);
    mockGetTasksRequest();

    await tasksPage.goto();

    await expect(page.getByText('No tasks found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty list', async ({page, tasksPage, mockGetTasksRequest}) => {
    mockGetTasksRequest();

    await tasksPage.goto({filter: 'completed', sortBy: 'creation'});

    await expect(page).toHaveScreenshot();
  });

  test('all open tasks', async ({page, tasksPage, mockGetTasksRequest}) => {
    mockGetTasksRequest([
      nonFormTask({
        assignee: 'jane',
      }),
      nonFormTask(),
    ]);

    await tasksPage.goto();

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tasks assigned to me', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
    mockGetTasksRequest([
      nonFormTask({
        assignee: 'demo',
      }),
      nonFormTask({
        assignee: 'demo',
      }),
    ]);

    await tasksPage.goto({filter: 'assigned-to-me', sortBy: 'follow-up'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('unassigned tasks', async ({page, tasksPage, mockGetTasksRequest}) => {
    mockGetTasksRequest([nonFormTask(), nonFormTask()]);

    await tasksPage.goto({filter: 'unassigned', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();
  });

  test('completed tasks', async ({page, tasksPage, mockGetTasksRequest}) => {
    mockGetTasksRequest([
      nonFormTask({
        assignee: 'demo',
        completionDate: '2024-04-17T16:57:41.000Z',
        taskState: 'COMPLETED',
      }),
      nonFormTask({
        assignee: 'jane',
        completionDate: '2024-04-17T16:57:41.000Z',
        taskState: 'COMPLETED',
      }),
    ]);

    await tasksPage.goto({filter: 'completed', sortBy: 'completion'});
    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle(/Created on.*/).hover();
    await expect(page).toHaveScreenshot();
    await task.getByTitle(/Completed on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by due date', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
    mockGetTasksRequest([
      nonFormTask({
        assignee: 'jane',
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
      }),
      nonFormTask({
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
      }),
    ]);

    await tasksPage.goto({filter: 'all-open', sortBy: 'due'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle(/Overdue.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by follow up date', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
    mockGetTasksRequest([
      nonFormTask({
        assignee: 'jane',
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
      }),
      nonFormTask({
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
      }),
    ]);
    await tasksPage.goto({filter: 'all-open', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle(/Follow-up on.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by priority', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
    mockGetTasksRequest([
      nonFormTask({
        assignee: 'jane',
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
        priority: 76,
      }),
      nonFormTask({
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
        priority: 51,
      }),
      nonFormTask({
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
        priority: 26,
      }),
      nonFormTask({
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
        priority: 1,
      }),
    ]);

    await tasksPage.goto({sortBy: 'priority'});

    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle('Critical').hover();

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and without variables', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
  }) => {
    const NON_FORM_TASK = nonFormTask();

    mockGetTasksRequest([NON_FORM_TASK]);
    mockGetTaskRequest(NON_FORM_TASK);
    mockGetTaskVariablesRequest({
      taskId: NON_FORM_TASK.id,
    });
    mockGetProcessRequest({
      id: NON_FORM_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and with variables', async ({
    page,
    taskVariableView,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
  }) => {
    const NON_FORM_TASK = nonFormTask({
      assignee: 'demo',
    });

    mockGetTasksRequest([NON_FORM_TASK]);
    mockGetTaskRequest(NON_FORM_TASK);
    mockGetTaskVariablesRequest({
      taskId: NON_FORM_TASK.id,
      variables,
    });
    mockGetProcessRequest({
      id: NON_FORM_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();

    await taskVariableView.addVariable({name: 'var', value: '"lorem ipsum"'});
    await expect(page.getByText('Complete task')).toBeEnabled();

    await expect(page).toHaveScreenshot();

    await page.getByLabel('Open JSON code editor').nth(0).hover();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
  }) => {
    const NON_FORM_TASK = nonFormTask({
      assignee: 'demo',
    });

    mockGetTasksRequest([NON_FORM_TASK]);
    mockGetTaskRequest(NON_FORM_TASK);
    mockGetTaskVariablesRequest({
      taskId: NON_FORM_TASK.id,
    });
    mockGetProcessRequest({
      id: NON_FORM_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
  }) => {
    const COMPLETED_TASK = nonFormTask({
      assignee: 'demo',
      taskState: 'COMPLETED',
      completionDate: '2024-04-18T16:57:41.000Z',
    });

    mockGetTasksRequest([COMPLETED_TASK]);
    mockGetTaskRequest(COMPLETED_TASK);
    mockGetTaskVariablesRequest({
      taskId: COMPLETED_TASK.id,
    });
    mockGetProcessRequest({
      id: COMPLETED_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });

    await tasksPage.gotoTaskDetails(COMPLETED_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task with variables', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
  }) => {
    const COMPLETED_TASK = nonFormTask({
      assignee: 'demo',
      taskState: 'COMPLETED',
      completionDate: '2024-04-18T16:57:41.000Z',
    });

    mockGetTasksRequest([COMPLETED_TASK]);
    mockGetTaskRequest(COMPLETED_TASK);
    mockGetTaskVariablesRequest({
      taskId: COMPLETED_TASK.id,
      variables,
    });
    mockGetProcessRequest({
      id: COMPLETED_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });

    await tasksPage.gotoTaskDetails(COMPLETED_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected unassigned task with form', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
    mockGetFormRequest,
  }) => {
    const FORM_TASK = formTask({
      assignee: null,
    });

    mockGetTasksRequest([FORM_TASK]);
    mockGetTaskRequest(FORM_TASK);
    mockGetTaskVariablesRequest({
      taskId: FORM_TASK.id,
      variables,
    });
    mockGetProcessRequest({
      id: FORM_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });
    mockGetFormRequest({
      formId: 'userTaskForm_1',
      processDefinitionKey: FORM_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetails(FORM_TASK.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task with form', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
    mockGetFormRequest,
  }) => {
    const FORM_TASK = formTask({
      assignee: 'demo',
    });

    mockGetTasksRequest([FORM_TASK]);
    mockGetTaskRequest(FORM_TASK);
    mockGetTaskVariablesRequest({
      taskId: FORM_TASK.id,
      variables,
    });
    mockGetProcessRequest({
      id: FORM_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });
    mockGetFormRequest({
      formId: 'userTaskForm_1',
      processDefinitionKey: FORM_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetails(FORM_TASK.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tenant on task detail', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
    mockGetFormRequest,
    mockClientConfigRequest,
  }) => {
    const NON_FORM_TASK_WITH_TENANT = nonFormTask({
      tenantId: MOCK_TENANTS[0].tenantId,
    });

    mockGetTasksRequest([NON_FORM_TASK_WITH_TENANT]);
    mockGetTaskRequest(NON_FORM_TASK_WITH_TENANT);
    mockGetTaskVariablesRequest({
      taskId: NON_FORM_TASK_WITH_TENANT.id,
    });
    mockGetProcessRequest({
      id: NON_FORM_TASK_WITH_TENANT.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });
    mockGetFormRequest({
      formId: 'userTaskForm_1',
      processDefinitionKey: NON_FORM_TASK_WITH_TENANT.processDefinitionKey,
    });
    mockClientConfigRequest({
      isEnterprise: false,
      canLogout: true,
      isLoginDelegated: false,
      contextPath: '',
      baseName: '',
      organizationId: null,
      clusterId: null,
      stage: null,
      mixpanelToken: null,
      mixpanelAPIHost: null,
      isMultiTenancyEnabled: true,
      clientMode: 'v1',
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK_WITH_TENANT.id);

    await expect(page).toHaveScreenshot();
  });

  test('expanded side panel', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
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

    mockGetTasksRequest();

    await tasksPage.goto();

    await tasksPage.expandSidePanelButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('custom filters modal', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
  }) => {
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

    mockGetTasksRequest();

    await tasksPage.goto();

    await tasksPage.expandSidePanelButton.click();
    await tasksPage.addCustomFilterButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('process view', async ({
    page,
    tasksPage,
    mockGetTasksRequest,
    mockGetTaskRequest,
    mockGetTaskVariablesRequest,
    mockGetProcessRequest,
    mockGetFormRequest,
  }) => {
    const FORM_TASK = formTask({
      assignee: 'demo',
      taskDefinitionId: 'Activity_0aecztp',
    });

    mockGetTasksRequest([FORM_TASK]);
    mockGetTaskRequest(FORM_TASK);
    mockGetTaskVariablesRequest({
      taskId: FORM_TASK.id,
      variables,
    });
    mockGetProcessRequest({
      id: FORM_TASK.processDefinitionKey,
      name: 'A test process',
      bpmnProcessId: 'someProcessId',
      version: 1,
      startEventFormId: '123456789',
    });
    mockGetFormRequest({
      formId: 'userTaskForm_1',
      processDefinitionKey: FORM_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetailsProcessTab(FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });
});
