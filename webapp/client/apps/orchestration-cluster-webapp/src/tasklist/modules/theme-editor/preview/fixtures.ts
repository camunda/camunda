/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog, CurrentUser, ProcessDefinition, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import previewFormSchema from './preview-form.form?raw';

/*
 * Sample data for the theme editor preview. Nothing here is sent to or read from the server.
 * Dates are relative to page load so labels such as "Overdue" and "Today" stay realistic.
 */

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const NOW = Date.now();

function at(offset: number) {
	return new Date(NOW + offset).toISOString();
}

const PREVIEW_USER: CurrentUser = {
	username: 'demo',
	displayName: 'Demo User',
	email: 'demo@example.com',
	salesPlanType: null,
	authorizedComponents: ['*'],
	roles: [],
	c8Links: {},
	tenants: [],
	groups: ['accounting'],
	canLogout: false,
};

let keyCounter = 2251799813690000;

function nextKey() {
	keyCounter += 1;
	return String(keyCounter);
}

function createTask(overrides: Partial<UserTask> & Pick<UserTask, 'name' | 'processName'>): UserTask {
	const processInstanceKey = nextKey();

	return {
		userTaskKey: nextKey(),
		state: 'CREATED',
		processDefinitionVersion: 1,
		processDefinitionId: 'my_simple_process',
		processInstanceKey,
		rootProcessInstanceKey: null,
		processDefinitionKey: '2251799813685279',
		elementId: 'task-1',
		elementInstanceKey: nextKey(),
		tenantId: '<default>',
		assignee: null,
		candidateGroups: [],
		candidateUsers: [],
		dueDate: null,
		followUpDate: null,
		creationDate: at(-2 * HOUR),
		completionDate: null,
		customHeaders: null,
		formKey: null,
		externalFormReference: null,
		businessId: null,
		tags: [],
		priority: 50,
		...overrides,
	};
}

const PREVIEW_TASKS: UserTask[] = [
	createTask({
		name: 'Fill in the customer form',
		processName: 'Customer onboarding',
		assignee: 'demo',
		priority: 80,
		dueDate: at(DAY),
		creationDate: at(-10 * 60 * 1000),
		formKey: 'camunda-forms:bpmn:bigForm',
		businessId: 'CUST-4471',
	}),
	createTask({
		name: 'Review invoice',
		processName: 'Invoice approval',
		assignee: 'demo',
		priority: 75,
		dueDate: at(2 * DAY),
		creationDate: at(-35 * 60 * 1000),
		candidateGroups: ['accounting'],
		businessId: 'INV-2026-0142',
	}),
	createTask({
		name: 'Resolve escalated ticket',
		processName: 'Support escalation',
		assignee: 'demo',
		priority: 100,
		dueDate: at(3 * HOUR),
		creationDate: at(-5 * HOUR),
		candidateGroups: ['support'],
	}),
	createTask({
		name: 'Check AI agent recommendation',
		processName: 'Claims handling',
		priority: 60,
		followUpDate: at(DAY),
		creationDate: at(-7 * HOUR),
		candidateGroups: ['claims'],
		businessId: 'CLM-88213',
	}),
	createTask({
		name: 'Verify customer identity',
		processName: 'Customer onboarding',
		assignee: 'jane',
		priority: 90,
		dueDate: at(-DAY),
		creationDate: at(-3 * DAY),
	}),
	createTask({
		name: 'Approve vacation request',
		processName: 'Leave management',
		priority: 40,
		creationDate: at(-DAY),
		candidateUsers: ['demo', 'jane'],
	}),
	createTask({
		name: 'Prepare contract draft',
		processName: 'Contract management',
		priority: 25,
		creationDate: at(-4 * DAY),
		followUpDate: at(5 * DAY),
	}),
	createTask({
		name: 'Schedule onboarding session',
		processName: 'Employee onboarding',
		assignee: 'demo',
		state: 'COMPLETED',
		creationDate: at(-6 * DAY),
		completionDate: at(-DAY),
	}),
	createTask({
		name: 'Confirm shipping address',
		processName: 'Order fulfillment',
		assignee: 'demo',
		state: 'COMPLETED',
		creationDate: at(-8 * DAY),
		completionDate: at(-2 * DAY),
	}),
];

function createVariable(task: UserTask, name: string, value: unknown): Variable {
	return {
		name,
		value: JSON.stringify(value),
		tenantId: '<default>',
		isTruncated: false,
		variableKey: `${task.userTaskKey}-${name}`,
		scopeKey: task.elementInstanceKey,
		processInstanceKey: task.processInstanceKey,
		rootProcessInstanceKey: null,
	};
}

function getPreviewVariables(task: UserTask): Variable[] {
	if (task.formKey !== null) {
		return [
			createVariable(task, 'tableSource', [
				{id: 1, name: 'Jane Doe', date: '2026-09-01'},
				{id: 2, name: 'John Smith', date: '2026-09-14'},
				{id: 3, name: 'Ada Lovelace', date: '2026-09-27'},
			]),
			createVariable(task, 'user', [
				{name: 'Jane', last_name: 'Doe', age: 34},
				{name: 'John', last_name: 'Smith', age: 41},
			]),
		];
	}

	return [
		createVariable(task, 'businessId', task.businessId ?? `REF-${task.userTaskKey.slice(-4)}`),
		createVariable(task, 'amount', 1250.5),
		createVariable(task, 'currency', 'EUR'),
		createVariable(task, 'approved', task.state === 'COMPLETED'),
		createVariable(task, 'requester', {name: 'Jane Doe', email: 'jane.doe@example.com'}),
		createVariable(task, 'lineItems', [
			{sku: 'A-100', quantity: 2},
			{sku: 'B-200', quantity: 1},
		]),
	];
}

function createAuditLog(task: UserTask, operationType: AuditLog['operationType'], offset: number): AuditLog {
	return {
		auditLogKey: `${task.userTaskKey}-${operationType}`,
		entityKey: task.userTaskKey,
		entityType: 'USER_TASK',
		operationType,
		batchOperationKey: null,
		batchOperationType: null,
		timestamp: at(offset),
		actorId: 'demo',
		actorType: 'USER',
		tenantId: '<default>',
		result: 'SUCCESS',
		category: 'USER_TASKS',
		processDefinitionId: task.processDefinitionId,
		processDefinitionKey: task.processDefinitionKey,
		processInstanceKey: task.processInstanceKey,
		rootProcessInstanceKey: null,
		elementInstanceKey: task.elementInstanceKey,
		jobKey: null,
		userTaskKey: task.userTaskKey,
		decisionRequirementsId: null,
		decisionRequirementsKey: null,
		decisionDefinitionId: null,
		decisionDefinitionKey: null,
		decisionEvaluationKey: null,
		deploymentKey: null,
		formKey: null,
		resourceKey: null,
		relatedEntityKey: null,
		relatedEntityType: null,
		entityDescription: null,
		agentElementId: null,
		inboundChannelType: null,
		inboundChannelToolName: null,
	};
}

function getPreviewAuditLogs(task: UserTask): AuditLog[] {
	const logs = [createAuditLog(task, 'CREATE', -2 * HOUR)];

	if (task.assignee !== null) {
		logs.push(createAuditLog(task, 'ASSIGN', -HOUR));
	}

	if (task.state === 'COMPLETED') {
		logs.push(createAuditLog(task, 'COMPLETE', -30 * 60 * 1000));
	}

	return logs.reverse();
}

function createProcess(overrides: Partial<ProcessDefinition> & Pick<ProcessDefinition, 'name'>): ProcessDefinition {
	return {
		resourceName: 'process.bpmn',
		version: 1,
		versionTag: null,
		processDefinitionId: overrides.name?.toLowerCase().replaceAll(' ', '_') ?? 'process',
		tenantId: '<default>',
		processDefinitionKey: nextKey(),
		hasStartForm: false,
		state: 'ACTIVE',
		...overrides,
	};
}

const PREVIEW_PROCESSES: ProcessDefinition[] = [
	createProcess({name: 'Invoice approval', version: 4, versionTag: 'v2.1', hasStartForm: true}),
	createProcess({name: 'Claims handling', version: 2}),
	createProcess({name: 'Customer onboarding', version: 7, hasStartForm: true}),
	createProcess({name: 'Leave management', version: 1}),
	createProcess({name: 'Order fulfillment', version: 3, versionTag: 'stable'}),
	createProcess({name: 'Support escalation', version: 5}),
];

/** A form-js schema that uses every common field type, attached to the task with a `formKey`. */
const PREVIEW_FORM_SCHEMA: string = previewFormSchema;

const PREVIEW_PROCESS_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_theme_preview" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="my_simple_process" isExecutable="true">
    <bpmn:startEvent id="start_event">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="start_event" targetRef="task-1" />
    <bpmn:userTask id="task-1" name="Review">
      <bpmn:extensionElements>
        <zeebe:userTask />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="Flow_2" sourceRef="task-1" targetRef="end_event" />
    <bpmn:endEvent id="end_event">
      <bpmn:incoming>Flow_2</bpmn:incoming>
    </bpmn:endEvent>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="my_simple_process">
      <bpmndi:BPMNShape id="start_event_di" bpmnElement="start_event">
        <dc:Bounds x="182" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="task-1_di" bpmnElement="task-1">
        <dc:Bounds x="270" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_event_di" bpmnElement="end_event">
        <dc:Bounds x="422" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="218" y="120" />
        <di:waypoint x="270" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="370" y="120" />
        <di:waypoint x="422" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

export {
	PREVIEW_FORM_SCHEMA,
	PREVIEW_PROCESS_XML,
	PREVIEW_PROCESSES,
	PREVIEW_TASKS,
	PREVIEW_USER,
	getPreviewAuditLogs,
	getPreviewVariables,
};
