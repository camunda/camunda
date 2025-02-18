/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from './visual-fixtures';
import schema from './resources/bigForm.json' assert {type: 'json'};
import {URL_API_V1_PATTERN} from '@/constants';

const MOCK_TASK = {
  id: 'task123',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  processDefinitionId: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  priority: 50,
  processDefinitionKey: '2251799813685255',
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
  context: null,
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
  tenantId: '<default>',
  implementation: 'JOB_WORKER',
};

test.describe('form-js integration', () => {
  test('check if Carbonization is working', async ({page}) => {
    page.setViewportSize({
      width: 1920,
      height: 10000,
    });
    await page.route(URL_API_V1_PATTERN, (route) => {
      if (route.request().url().includes('v1/tasks/task123/variables/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/tasks/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([MOCK_TASK]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/tasks/task123')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(MOCK_TASK),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/forms/userTaskForm_1')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'userTaskForm_3j0n396',
            processDefinitionKey: '2251799813685255',
            schema: JSON.stringify(schema),
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('/v2/authentication/me')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            userId: 'demo',
            displayName: 'demo',
            permissions: ['READ', 'WRITE'],
            salesPlanType: null,
            roles: null,
            c8Links: [],
            tenants: [],
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (
        route.request().url().includes('v1/internal/processes/2251799813685255')
      ) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: '2251799813685255',
            name: 'Big form process',
            bpmnProcessId: 'bigFormProcess',
            sortValues: null,
            version: 1,
            startEventFormId: null,
            tenantId: '<default>',
            bpmnXml: `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_0qcd08n" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.27.0-nightly.20240903" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.6.0">
  <bpmn:process id="user_task_process" name="User task process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_0js3bk8</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_0js3bk8" sourceRef="StartEvent_1" targetRef="foobar" />
    <bpmn:endEvent id="Event_1h15025">
      <bpmn:incoming>Flow_0wxs5w5</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0wxs5w5" sourceRef="foobar" targetRef="Event_1h15025" />
    <bpmn:userTask id="foobar">
      <bpmn:extensionElements>
        <zeebe:formDefinition formId="cv_form" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_0js3bk8</bpmn:incoming>
      <bpmn:outgoing>Flow_0wxs5w5</bpmn:outgoing>
    </bpmn:userTask>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="user_task_process">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1h15025_di" bpmnElement="Event_1h15025">
        <dc:Bounds x="392" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0s7fw2e_di" bpmnElement="foobar">
        <dc:Bounds x="240" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0js3bk8_di" bpmnElement="Flow_0js3bk8">
        <di:waypoint x="188" y="120" />
        <di:waypoint x="240" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0wxs5w5_di" bpmnElement="Flow_0wxs5w5">
        <di:waypoint x="340" y="120" />
        <di:waypoint x="392" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`,
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto(`/${MOCK_TASK.id}/`, {
      waitUntil: 'networkidle',
    });

    await expect(page.locator('.fjs-container')).toHaveScreenshot();
  });
});
