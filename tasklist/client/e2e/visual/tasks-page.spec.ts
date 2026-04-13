/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test, MOCK_TENANTS} from '@/fixtures/v2-visual';
import {unassignedTask} from '@/mocks/v2/task';
import {variables} from '@/mocks/v2/variables';

test.describe('tasks page', () => {
  test('empty state', async ({page, tasksPage, mockQueryUserTasksRequest}) => {
    mockQueryUserTasksRequest();

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
  }) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('theme', '"dark"');
    })()`);
    mockQueryUserTasksRequest();

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state when completed task before', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
  }) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasCompletedTask', 'true');
    })()`);
    mockQueryUserTasksRequest();

    await tasksPage.goto();

    await expect(page.getByText('No tasks found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty list', async ({page, tasksPage, mockQueryUserTasksRequest}) => {
    mockQueryUserTasksRequest();

    await tasksPage.goto({filter: 'completed', sortBy: 'creation'});

    await expect(page).toHaveScreenshot();
  });

  test('all open tasks', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
  }) => {
    mockQueryUserTasksRequest([
      unassignedTask({
        assignee: 'jane',
      }),
      unassignedTask(),
    ]);

    await tasksPage.goto();

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tasks assigned to me', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
  }) => {
    mockQueryUserTasksRequest([
      unassignedTask({
        assignee: 'demo',
      }),
      unassignedTask({
        assignee: 'demo',
      }),
    ]);

    await tasksPage.goto({filter: 'assigned-to-me', sortBy: 'follow-up'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('unassigned tasks', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
  }) => {
    mockQueryUserTasksRequest([unassignedTask(), unassignedTask()]);

    await tasksPage.goto({filter: 'unassigned', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();
  });

  test('completed tasks', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
  }) => {
    mockQueryUserTasksRequest([
      unassignedTask({
        assignee: 'demo',
        completionDate: '2024-04-17T16:57:41.000Z',
        state: 'COMPLETED',
      }),
      unassignedTask({
        assignee: 'jane',
        completionDate: '2024-04-17T16:57:41.000Z',
        state: 'COMPLETED',
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
    mockQueryUserTasksRequest,
  }) => {
    mockQueryUserTasksRequest([
      unassignedTask({
        assignee: 'jane',
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
      }),
      unassignedTask({
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
    mockQueryUserTasksRequest,
  }) => {
    mockQueryUserTasksRequest([
      unassignedTask({
        assignee: 'jane',
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
      }),
      unassignedTask({
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
    mockQueryUserTasksRequest,
  }) => {
    mockQueryUserTasksRequest([
      unassignedTask({
        assignee: 'jane',
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
        priority: 76,
      }),
      unassignedTask({
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
        priority: 51,
      }),
      unassignedTask({
        creationDate: '2024-04-13T16:57:41.025+0000',
        followUpDate: '2024-04-19T16:57:41.000Z',
        dueDate: '2024-04-18T16:57:41.000Z',
        priority: 26,
      }),
      unassignedTask({
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
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
  }) => {
    const NON_FORM_TASK = unassignedTask();

    mockQueryUserTasksRequest([NON_FORM_TASK]);
    mockGetUserTaskRequest(NON_FORM_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: NON_FORM_TASK.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: NON_FORM_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.userTaskKey);

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and with variables', async ({
    page,
    taskVariableView,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
  }) => {
    const NON_FORM_TASK = unassignedTask({
      assignee: 'demo',
    });

    mockQueryUserTasksRequest([NON_FORM_TASK]);
    mockGetUserTaskRequest(NON_FORM_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: NON_FORM_TASK.userTaskKey,
      variables,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: NON_FORM_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.userTaskKey);

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
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
  }) => {
    const NON_FORM_TASK = unassignedTask({
      assignee: 'demo',
    });

    mockQueryUserTasksRequest([NON_FORM_TASK]);
    mockGetUserTaskRequest(NON_FORM_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: NON_FORM_TASK.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: NON_FORM_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.userTaskKey);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
  }) => {
    const COMPLETED_TASK = unassignedTask({
      assignee: 'demo',
      state: 'COMPLETED',
      completionDate: '2024-04-18T16:57:41.000Z',
    });

    mockQueryUserTasksRequest([COMPLETED_TASK]);
    mockGetUserTaskRequest(COMPLETED_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: COMPLETED_TASK.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: COMPLETED_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetails(COMPLETED_TASK.userTaskKey);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task with variables', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
  }) => {
    const COMPLETED_TASK = unassignedTask({
      assignee: 'demo',
      state: 'COMPLETED',
      completionDate: '2024-04-18T16:57:41.000Z',
    });

    mockQueryUserTasksRequest([COMPLETED_TASK]);
    mockGetUserTaskRequest(COMPLETED_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: COMPLETED_TASK.userTaskKey,
      variables,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: COMPLETED_TASK.processDefinitionKey,
    });

    await tasksPage.gotoTaskDetails(COMPLETED_TASK.userTaskKey);

    await expect(page).toHaveScreenshot();
  });

  test('selected unassigned task with form', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
    mockGetUserTaskFormRequest,
  }) => {
    const FORM_TASK = unassignedTask({
      formKey: 'bigForm',
    });

    mockQueryUserTasksRequest([FORM_TASK]);
    mockGetUserTaskRequest(FORM_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: FORM_TASK.userTaskKey,
      variables,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: FORM_TASK.processDefinitionKey,
    });
    mockGetUserTaskFormRequest({
      userTaskKey: FORM_TASK.userTaskKey,
    });

    await tasksPage.gotoTaskDetails(FORM_TASK.userTaskKey);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task with form', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
    mockGetUserTaskFormRequest,
  }) => {
    const FORM_TASK = unassignedTask({
      formKey: 'bigForm',
      assignee: 'demo',
    });

    mockQueryUserTasksRequest([FORM_TASK]);
    mockGetUserTaskRequest(FORM_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: FORM_TASK.userTaskKey,
      variables,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: FORM_TASK.processDefinitionKey,
    });
    mockGetUserTaskFormRequest({
      userTaskKey: FORM_TASK.userTaskKey,
    });

    await tasksPage.gotoTaskDetails(FORM_TASK.userTaskKey);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tenant on task detail', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
    mockGetUserTaskFormRequest,
    mockClientConfigRequest,
  }) => {
    const NON_FORM_TASK_WITH_TENANT = unassignedTask({
      tenantId: MOCK_TENANTS[0].tenantId,
    });

    mockQueryUserTasksRequest([NON_FORM_TASK_WITH_TENANT]);
    mockGetUserTaskRequest(NON_FORM_TASK_WITH_TENANT);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: NON_FORM_TASK_WITH_TENANT.userTaskKey,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: NON_FORM_TASK_WITH_TENANT.processDefinitionKey,
    });
    mockGetUserTaskFormRequest({
      userTaskKey: NON_FORM_TASK_WITH_TENANT.userTaskKey,
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
      clientMode: 'v2',
    });

    await tasksPage.gotoTaskDetails(NON_FORM_TASK_WITH_TENANT.userTaskKey);

    await expect(page).toHaveScreenshot();
  });

  test('expanded side panel', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
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

    mockQueryUserTasksRequest();

    await tasksPage.goto();

    await tasksPage.expandSidePanelButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('custom filters modal', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
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

    mockQueryUserTasksRequest();

    await tasksPage.goto();

    await tasksPage.expandSidePanelButton.click();
    await tasksPage.addCustomFilterButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('process view', async ({
    page,
    tasksPage,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetProcessDefinitionXmlRequest,
    mockGetUserTaskFormRequest,
  }) => {
    const FORM_TASK = unassignedTask({
      formKey: 'bigForm',
      assignee: 'demo',
      elementId: 'Activity_0aecztp',
    });

    mockQueryUserTasksRequest([FORM_TASK]);
    mockGetUserTaskRequest(FORM_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: FORM_TASK.userTaskKey,
      variables,
    });
    mockGetProcessDefinitionXmlRequest({
      processDefinitionKey: FORM_TASK.processDefinitionKey,
    });
    mockGetUserTaskFormRequest({
      userTaskKey: FORM_TASK.userTaskKey,
    });

    await tasksPage.gotoTaskDetailsProcessTab(FORM_TASK.userTaskKey);

    await expect(page).toHaveScreenshot();
  });
});
