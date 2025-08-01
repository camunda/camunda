/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page, expect} from '@playwright/test';
import {test} from '@/fixtures/test';
import {sub as subTime} from 'date-fns/sub';
import {add as addTime} from 'date-fns/add';
import registerPassengerForm from '@/resources/registerPassenger.json' assert {type: 'json'};
import registerPassengerStartForm from '@/resources/registerPassengerStartForm.json' assert {type: 'json'};

const now = new Date();

type TaskSpec = {
  name: string;
  processName: string;
  creationDate: Date;
  assignee: string | null;
  taskState: string;
  dueDate?: Date;
  completionDate?: Date;
  priority?: number;
  followUpDate?: Date;
  context?: string;
};

type ProcessSpec = {
  name: string;
  bpmnProcessId: string;
  startEventFormId?: string;
};

const MOCK_TASKLIST: TaskSpec[] = [
  {
    name: 'Check for payment',
    processName: 'Order Process',
    taskState: 'CREATED',
    assignee: null,
    creationDate: subTime(now, {days: 2, minutes: 43}),
    followUpDate: addTime(now, {days: 12, minutes: 45}),
  },
  {
    name: 'Register the passenger',
    processName: 'Flight registration',
    taskState: 'CREATED',
    assignee: null,
    creationDate: subTime(now, {days: 0, minutes: 12}),
    dueDate: addTime(now, {days: 0, minutes: 120}),
    context: 'ARRON A. ARRONSON',
  },
  {
    name: 'Register the passenger',
    processName: 'Flight registration',
    taskState: 'CREATED',
    assignee: 'demo',
    creationDate: subTime(now, {days: 0, minutes: 36}),
    dueDate: addTime(now, {days: 0, minutes: 120}),
    context: 'BOB MONKHOUSE',
  },
  {
    name: 'Register the passenger',
    processName: 'Flight registration',
    taskState: 'CREATED',
    assignee: null,
    creationDate: subTime(now, {days: 1, minutes: 0}),
    dueDate: addTime(now, {days: 0, minutes: 120}),
    context: 'CLAIRE WOODS',
  },
];

const MOCK_PROCESSES: ProcessSpec[] = [
  {
    name: 'Order flight',
    bpmnProcessId: 'order-flight',
  },
  {
    name: 'Register a passenger',
    bpmnProcessId: 'register',
    startEventFormId: 'form',
  },
  {
    name: 'Refund passenger',
    bpmnProcessId: 'refund',
    startEventFormId: 'form',
  },
];

function toTask(
  id: string,
  {
    name,
    processName,
    assignee,
    creationDate,
    dueDate,
    followUpDate,
    completionDate,
    priority,
    taskState,
    context,
  }: TaskSpec,
) {
  return {
    id,
    name: name,
    taskDefinitionId: `Task_${id}`,
    processName: processName,
    creationDate: creationDate.toISOString(),
    completionDate: completionDate ? completionDate.toISOString() : null,
    priority: priority ?? 50,
    assignee: assignee,
    taskState: taskState,
    sortValues: ['1715692084940', '2251799813685299'],
    isFirst: true,
    formKey: `camunda-forms:bpmn:form`,
    formId: null,
    formVersion: null,
    isFormEmbedded: true,
    processDefinitionKey: '2251799813685257',
    processInstanceKey: '2251799813685286',
    tenantId: '<default>',
    dueDate: dueDate ? dueDate.toISOString() : null,
    followUpDate: followUpDate ? followUpDate.toISOString() : null,
    candidateGroups: ['sales', 'accounting'],
    candidateUsers: ['demo', 'admin'],
    variables: [],
    context: context ? context : null,
    implementation: 'JOB_WORKER',
  };
}

async function mockTaskSearch(page: Page, specs: TaskSpec[]) {
  await page.route(/^.*\/v1\/tasks\/search$/i, (route) =>
    route.fulfill({
      status: 200,
      body: JSON.stringify(specs.map((spec, i) => toTask(`${i}`, spec))),
      headers: {
        'content-type': 'application/json',
      },
    }),
  );
}

async function mockForm(page: Page, schema: unknown) {
  await page.route(/^.*\/v1\/forms\/form\?.*$/i, (route) =>
    route.fulfill({
      status: 200,
      body: JSON.stringify({
        id: 'form',
        processDefinitionKey: '2251799813685255',
        schema: JSON.stringify(schema),
      }),
      headers: {
        'content-type': 'application/json',
      },
    }),
  );
}

async function mockTask(
  page: Page,
  taskId: string,
  task: TaskSpec,
  variables: unknown[],
  formSchema: unknown,
) {
  await page.route(/^.*\/v1\/tasks\/[0-9]+$/i, (route) =>
    route.fulfill({
      status: 200,
      body: JSON.stringify(toTask(taskId, task)),
      headers: {
        'content-type': 'application/json',
      },
    }),
  );
  await page.route(/^.*\/v1\/tasks\/[0-9]+\/variables\/search$/i, (route) =>
    route.fulfill({
      status: 200,
      body: JSON.stringify(variables),
      headers: {
        'content-type': 'application/json',
      },
    }),
  );
  await mockForm(page, formSchema);
}

async function mockProcessSearch(page: Page, processSpecs: ProcessSpec[]) {
  const processes = processSpecs.map(
    ({name, bpmnProcessId, startEventFormId}, i) => ({
      id: `${i}`,
      name: name,
      bpmnProcessId: bpmnProcessId,
      sortValues: null,
      version: 1,
      startEventFormId: startEventFormId ? startEventFormId : null,
      tenantId: '<default>',
      bpmnXml: null,
    }),
  );
  await page.route(/^.*\/v1\/internal\/processes$/i, (route) => {
    return route.fulfill({
      status: 200,
      body: JSON.stringify(processes),
      headers: {
        'content-type': 'application/json',
      },
    });
  });
  await page.route(/^.*\/v1\/internal\/processes\?.*$/i, (route, req) => {
    const params = new URLSearchParams(req.url().split('?')[1]);
    const query = params.get('query');
    return route.fulfill({
      status: 200,
      body: JSON.stringify(
        query
          ? processes.filter(
              (p) => p.name.includes(query) || p.bpmnProcessId.includes(query),
            )
          : processes,
      ),
      headers: {
        'content-type': 'application/json',
      },
    });
  });
}

async function mockProcessStart(page: Page) {
  await page.route(/^.*\/v1\/internal\/processes\/[^/]*\/start$/i, (route) => {
    return route.fulfill({
      status: 200,
      body: JSON.stringify({id: '1234'}),
      headers: {
        'content-type': 'application/json',
      },
    });
  });
}

async function mockCurrentUser(page: Page) {
  await page.route(/^.*\/v2\/authentication\/me$/i, (route) =>
    route.fulfill({
      status: 200,
      body: JSON.stringify({
        username: 'demo',
        displayName: 'demo',
        salesPlanType: null,
        roles: null,
        c8Links: [],
        tenants: [],
      }),
      headers: {
        'content-type': 'application/json',
      },
    }),
  );
}

async function mockClientConfig(page: Page) {
  await page.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: `window.clientConfig = {
        "isEnterprise":true,
        "canLogout":true,
        "isLoginDelegated":false,
        "contextPath":"",
        "baseName":"/",
        "organizationId":null,
        "clusterId":null,
        "stage":null,
        "mixpanelToken":null,
        "mixpanelAPIHost":null,
        "isMultiTenancyEnabled": false
      };`,
    }),
  );
}

test.beforeEach(async ({page}) => {
  await mockCurrentUser(page);
  await mockClientConfig(page);
  await page.addInitScript(`(() => {
    window.localStorage.setItem('hasConsentedToStartProcess', 'true');
  })()`);
});

test.describe('Tasklist snapshots', () => {
  test('empty tasklist', async ({page, tasksPage}) => {
    await mockTaskSearch(page, []);
    await tasksPage.goto();
    await expect(page).toHaveScreenshot();
  });

  test('populated tasklist', async ({page, tasksPage}) => {
    await mockTaskSearch(page, MOCK_TASKLIST);
    await tasksPage.goto();
    await expect(page).toHaveScreenshot();
  });

  test('populated tasklist ordering', async ({page, tasksPage}) => {
    await mockTaskSearch(page, MOCK_TASKLIST);

    await tasksPage.goto();

    await page.getByRole('button', {name: 'Sort tasks'}).click();

    const viewport = page.viewportSize();

    await expect(page).toHaveScreenshot({
      clip: {
        x: 0,
        y: 0,
        width: viewport?.width ?? 1024,
        height: 300,
      },
    });
  });

  test('claim a task', async ({page, tasksPage}) => {
    await mockTaskSearch(page, MOCK_TASKLIST);
    await mockTask(
      page,
      '1',
      MOCK_TASKLIST[1],
      [
        {
          name: 'passengerName',
          value: '"ARRON A. ARRONSON"',
          isValueTruncated: false,
          previewValue: '""',
          draft: null,
        },
      ],
      registerPassengerForm,
    );

    await tasksPage.gotoTaskDetails('1');

    await expect(page.getByText('Register Passenger')).toBeVisible();
    await expect(page.getByText('Name: ARRON A. ARRONSON')).toBeVisible();

    await page.getByText('Assign to me').focus();
    await expect(page).toHaveScreenshot();
  });

  test('claimed a task', async ({page, tasksPage}) => {
    const alteredTaskList = [
      ...MOCK_TASKLIST.slice(0, 1),
      {...MOCK_TASKLIST[1], assignee: 'demo'},
      ...MOCK_TASKLIST.slice(2),
    ];
    await mockTaskSearch(page, alteredTaskList);
    await mockTask(
      page,
      '1',
      {...alteredTaskList[1], assignee: 'demo'},
      [
        {
          name: 'passengerName',
          value: '"ARRON A. ARRONSON"',
          isValueTruncated: false,
          previewValue: '""',
          draft: null,
        },
      ],
      registerPassengerForm,
    );

    await tasksPage.gotoTaskDetails('1');

    await expect(page.getByText('Register Passenger')).toBeVisible();
    await expect(page.getByText('Name: ARRON A. ARRONSON')).toBeVisible();

    await expect(page.getByText('Complete Task')).toBeVisible();
    await expect(page.getByText('Complete Task')).toBeEnabled();

    await expect(page).toHaveScreenshot();
  });

  test('completed a task', async ({page, tasksPage}) => {
    const completedTaskList = [
      {
        ...MOCK_TASKLIST[1],
        assignee: 'demo',
        taskState: 'COMPLETE',
        completionDate: now,
      },
    ];
    await mockTaskSearch(page, completedTaskList);
    await mockTask(
      page,
      '1',
      {...completedTaskList[0], assignee: 'demo'},
      [
        {
          name: 'passengerName',
          value: '"ARRON A. ARRONSON"',
          isValueTruncated: false,
          previewValue: '""',
          draft: null,
        },
      ],
      registerPassengerForm,
    );

    await tasksPage.goto({filter: 'completed', sortBy: 'completion'});

    await tasksPage.openTask(completedTaskList[0].name);

    await expect(page.getByText('Register Passenger')).toBeVisible();
    await expect(page.getByText('Name: ARRON A. ARRONSON')).toBeVisible();

    await expect(page.getByText('Complete Task')).toBeVisible();
    await expect(page.getByText('Complete Task')).not.toBeEnabled();
    await page.getByTestId('scrollable-list').click({position: {x: 0, y: 300}}); // Deselect the button

    await expect(page).toHaveScreenshot();
  });

  test('processes list', async ({page, processesPage}) => {
    await mockProcessSearch(page, MOCK_PROCESSES);

    await processesPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('processes search', async ({page, processesPage}) => {
    await mockProcessSearch(page, MOCK_PROCESSES);

    await processesPage.goto();
    await processesPage.searchForProcess('order');
    await expect(async () => {
      expect(await page.getByTestId('process-tile').all()).toHaveLength(1);
    }).toPass();

    await expect(page).toHaveScreenshot();
  });

  test('processes start form', async ({page, processesPage}) => {
    await mockProcessSearch(page, MOCK_PROCESSES);
    await mockForm(page, registerPassengerStartForm);

    await processesPage.goto();
    await page
      .getByTestId('process-tile')
      .filter({has: page.getByText('Register')})
      .getByRole('button', {name: 'Start process'})
      .click();

    await expect(page).toHaveScreenshot();
  });

  test('processes start', async ({page, processesPage}) => {
    await mockProcessSearch(page, MOCK_PROCESSES);
    await mockProcessStart(page);

    await processesPage.goto();
    await page
      .getByTestId('process-tile')
      .filter({has: page.getByText('Order')})
      .getByRole('button', {name: 'Start process'})
      .click();

    await expect(page.getByText('Process has started')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('advanced filters', async ({page, tasksPage}) => {
    await mockTaskSearch(page, MOCK_TASKLIST);

    await tasksPage.goto();
    await tasksPage.expandSidePanelButton.click();
    await tasksPage.addCustomFilterButton.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    await expect(dialog.getByText('Apply filters')).toBeVisible();

    await expect(page).toHaveScreenshot();

    await dialog
      .getByRole('switch', {name: 'Advanced filters'})
      .click({force: true});

    const taskVariables = dialog.getByRole('group', {name: 'Task Variables'});
    await expect(taskVariables).toBeVisible();

    await taskVariables.getByRole('button', {name: 'Add variable'}).click();
    await taskVariables.getByRole('textbox', {name: 'Name'}).fill('user');
    await taskVariables.getByRole('textbox', {name: 'Value'}).fill('"alice"');

    await dialog.click(); // Get rid of the textbox focus

    await page.setViewportSize({width: 1280, height: 1000});
    await expect(page).toHaveScreenshot();
  });
});
