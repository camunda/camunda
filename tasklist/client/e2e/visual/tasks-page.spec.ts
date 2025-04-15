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

type Task = {
  id: string;
  name: string;
  taskDefinitionId: string;
  processName: string;
  creationDate: string;
  followUpDate: string | null;
  dueDate: string | null;
  completionDate: string | null;
  priority: number | null;
  assignee: string | null;
  taskState: string;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  formVersion: number | null | undefined;
  formId: string | null;
  isFormEmbedded: boolean | null;
  processInstanceKey: string;
  processDefinitionKey: string;
  candidateGroups: string[] | null;
  candidateUsers: string[] | null;
  tenantId: string | '<default>' | null;
  context: string | null;
};

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

const NON_FORM_TASK: Task = {
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
  priority: 50,
  candidateGroups: ['demo group'],
  candidateUsers: ['demo'],
  followUpDate: null,
  dueDate: null,
  sortValues: ['1684881752515', '4503599627371089'],
  isFirst: true,
  tenantId: null,
  context: null,
};

const NON_FORM_TASK_EMPTY_VARIABLES: unknown[] = [] as const;

const NON_FORM_TASK_VARIABLES = [
  {
    id: '2251799813686711-small',
    name: 'small',
    previewValue: '"Hello World"',
    value: '"Hello World"',
    isValueTruncated: false,
  },
];

const FORM_TASK: Task = {
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
  priority: 50,
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: [],
  candidateUsers: [],
  sortValues: ['1684881752515', '4503599627371089'],
  isFirst: true,
  tenantId: null,
  context: null,
};

const BPMN_XML = `
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_11n4bz1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.20.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.4.0">
  <bpmn:process id="Process_10qar8i" name="oioopiihhio" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_0jqzoa5</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:task id="Activity_0aecztp" name="A task">
      <bpmn:incoming>Flow_0jqzoa5</bpmn:incoming>
      <bpmn:outgoing>Flow_0f4sn94</bpmn:outgoing>
    </bpmn:task>
    <bpmn:sequenceFlow id="Flow_0jqzoa5" sourceRef="StartEvent_1" targetRef="Activity_0aecztp" />
    <bpmn:endEvent id="Event_0uwu5c4">
      <bpmn:incoming>Flow_0f4sn94</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0f4sn94" sourceRef="Activity_0aecztp" targetRef="Event_0uwu5c4" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_10qar8i">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="179" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0aecztp_di" bpmnElement="Activity_0aecztp">
        <dc:Bounds x="270" y="77" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0uwu5c4_di" bpmnElement="Event_0uwu5c4">
        <dc:Bounds x="432" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0jqzoa5_di" bpmnElement="Flow_0jqzoa5">
        <di:waypoint x="215" y="117" />
        <di:waypoint x="270" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0f4sn94_di" bpmnElement="Flow_0f4sn94">
        <di:waypoint x="370" y="117" />
        <di:waypoint x="432" y="117" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

function mockResponses(
  tasks: Array<Task> = [],
  task: Task = NON_FORM_TASK,
  variables: unknown[] = NON_FORM_TASK_EMPTY_VARIABLES,
  bpmnXml: string | undefined = BPMN_XML,
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

    return route.continue();
  };
}

test.describe('tasks page', () => {
  test('empty state', async ({page, tasksPage}) => {
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state dark theme', async ({page, tasksPage}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('theme', '"dark"');
    })()`);
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await tasksPage.goto();

    await expect(page).toHaveScreenshot();
  });

  test('empty state when completed task before', async ({page, tasksPage}) => {
    await page.addInitScript(`(() => {
      window.localStorage.setItem('hasCompletedTask', 'true');
    })()`);
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await tasksPage.goto();

    await expect(page.getByText('No tasks found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty list', async ({page, tasksPage}) => {
    await page.route(/^.*\/v1.*$/i, mockResponses());

    await tasksPage.goto({filter: 'completed', sortBy: 'creation'});

    await expect(page).toHaveScreenshot();
  });

  test('all open tasks', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
      ]),
    );

    await tasksPage.goto();

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tasks assigned to me', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
      ]),
    );

    await tasksPage.goto({filter: 'assigned-to-me', sortBy: 'follow-up'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('unassigned tasks', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
      ]),
    );

    await tasksPage.goto({filter: 'unassigned', sortBy: 'follow-up'});

    await expect(page).toHaveScreenshot();
  });

  test('completed tasks', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'demo',
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: '2024-04-17T16:57:41.000Z',
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'COMPLETED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: '2024-04-17T16:57:41.000Z',
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
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
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
      ]),
    );

    await tasksPage.goto({filter: 'all-open', sortBy: 'due'});

    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await expect(page).toHaveScreenshot();

    const task = tasksPage.task('Register the passenger');
    await task.getByTitle(/Overdue.*/).hover();
    await expect(page).toHaveScreenshot();
  });

  test('tasks ordered by follow up date', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 50,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          priority: 50,
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
          tenantId: '<default>',
        },
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
      /^.*\/v1.*$/i,
      mockResponses([
        {
          id: '2251799813686198',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: 'jane',
          creationDate: '2024-04-13T16:57:41.025+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686198'],
          isFirst: true,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 76,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 51,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 26,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
        {
          id: '2251799813686256',
          name: 'Register the passenger',
          processName: 'Flight registration',
          assignee: null,
          creationDate: '2024-04-13T16:57:41.067+0000',
          followUpDate: '2024-04-19T16:57:41.000Z',
          dueDate: '2024-04-18T16:57:41.000Z',
          taskState: 'CREATED',
          sortValues: ['1681923461000', '2251799813686256'],
          isFirst: false,
          taskDefinitionId: 'registerPassenger',
          completionDate: null,
          priority: 1,
          formKey: null,
          formId: null,
          formVersion: null,
          isFormEmbedded: true,
          processDefinitionKey: '2251799813685251',
          processInstanceKey: '4503599627371064',
          candidateGroups: null,
          candidateUsers: null,
          context: null,
          tenantId: '<default>',
        },
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
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: null,
            creationDate: '2024-04-13T16:57:41.482+0000',
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
            priority: 50,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
            tenantId: '<default>',
          },
        ],
        NON_FORM_TASK,
      ),
    );

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected task without a form and with variables', async ({
    page,
    taskVariableView,
    tasksPage,
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
            creationDate: '2024-04-13T16:57:41.482+0000',
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
            priority: 50,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
            tenantId: '<default>',
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
        },
        NON_FORM_TASK_VARIABLES,
      ),
    );

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();

    await taskVariableView.addVariable({name: 'var', value: '"lorem ipsum"'});
    await expect(page.getByText('Complete task')).toBeEnabled();

    await expect(page).toHaveScreenshot();

    await page.getByLabel('Open JSON code editor').nth(0).hover();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2024-04-13T16:57:41.482+0000',
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
            priority: 50,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
            tenantId: '<default>',
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
        },
      ),
    );

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2024-04-13T16:57:41.482+0000',
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
            priority: 50,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
            tenantId: '<default>',
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2024-04-18T16:57:41.000Z',
        },
      ),
    );

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected completed task with variables', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687061',
            name: 'Activity_1ygafd4',
            processName: 'TwoUserTasks',
            assignee: 'demo',
            creationDate: '2024-04-13T16:57:41.482+0000',
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
            priority: 50,
            candidateGroups: ['demo group'],
            candidateUsers: ['demo'],
            context: null,
            tenantId: '<default>',
          },
        ],
        {
          ...NON_FORM_TASK,
          assignee: 'demo',
          taskState: 'COMPLETED',
          completionDate: '2024-04-18T16:57:41.000Z',
        },
        NON_FORM_TASK_VARIABLES,
      ),
    );

    await tasksPage.gotoTaskDetails(NON_FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });

  test('selected unassigned task with form', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: null,
            creationDate: '2024-04-13T16:57:41.475+0000',
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
            priority: 50,
            taskDefinitionId: 'Activity_0aecztp',
            processInstanceKey: '4503599627371425',
            candidateGroups: null,
            candidateUsers: null,
            context: null,
            tenantId: '<default>',
          },
        ],
        {
          ...FORM_TASK,
          assignee: null,
        },
      ),
    );

    await tasksPage.gotoTaskDetails(FORM_TASK.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('selected assigned task with form', async ({page, tasksPage}) => {
    await page.route(
      /^.*\/v1.*$/i,
      mockResponses(
        [
          {
            id: '2251799813687045',
            name: 'Big form task',
            processName: 'Big form process',
            assignee: 'demo',
            creationDate: '2024-04-13T16:57:41.475+0000',
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
            priority: 50,
            taskDefinitionId: 'Activity_0aecztp',
            processInstanceKey: '4503599627371425',
            candidateGroups: null,
            candidateUsers: null,
            context: null,
            tenantId: '<default>',
          },
        ],
        {
          ...FORM_TASK,
          assignee: 'demo',
        },
      ),
    );

    await tasksPage.gotoTaskDetails(FORM_TASK.id);

    await expect(page.getByText('I am a textfield*')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('tenant on task detail', async ({page, tasksPage}) => {
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
        "baseName":"",
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

    await page.route(/^.*\/v1.*$/i, mockResponses());

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

    await page.route(/^.*\/v1.*$/i, mockResponses());

    await tasksPage.goto();

    await tasksPage.expandSidePanelButton.click();
    await tasksPage.addCustomFilterButton.click();

    await expect(page).toHaveScreenshot();
  });

  test('process view', async ({page, tasksPage}) => {
    await page.route(/^.*\/v1.*$/i, mockResponses([FORM_TASK], FORM_TASK));

    await tasksPage.gotoTaskDetailsProcessTab(FORM_TASK.id);

    await expect(page).toHaveScreenshot();
  });
});
