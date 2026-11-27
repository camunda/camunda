/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import type {CaseModel} from '@camunda/case-management-sdk';
import type {AuditLog, ElementInstance, UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {getCaseActivity} from './getCaseActivity';

const PROCESS_INSTANCE_KEY = '100';
const TIMESTAMP = '2026-11-27T10:00:00.000Z';
const LATER_TIMESTAMP = '2026-11-27T10:05:00.000+01:00';

const model: CaseModel = {
	caseMarker: {titleVariable: null, caseFormId: null},
	stages: [
		{
			id: 'review',
			name: 'Review',
			kind: 'subProcess',
			elementIds: ['checkDocuments', 'scoring'],
			adHoc: null,
			manualCompletion: null,
		},
	],
	hasCallActivity: false,
	elementNames: new Map([
		['checkDocuments', 'Check documents'],
		['scoring', 'Score applicant'],
	]),
	elementTypes: new Map(),
	eventTriggers: new Map(),
	stageIdByElementId: new Map([
		['checkDocuments', 'review'],
		['scoring', 'review'],
	]),
};

function createAuditLog(overrides: Partial<AuditLog>): AuditLog {
	return {
		auditLogKey: '1',
		entityKey: PROCESS_INSTANCE_KEY,
		entityType: 'PROCESS_INSTANCE',
		operationType: 'CREATE',
		batchOperationKey: null,
		batchOperationType: null,
		timestamp: TIMESTAMP,
		actorId: 'demo',
		actorType: 'USER',
		agentElementId: null,
		tenantId: '<default>',
		result: 'SUCCESS',
		category: 'DEPLOYED_RESOURCES',
		processDefinitionId: 'loan',
		processDefinitionKey: '1',
		processInstanceKey: PROCESS_INSTANCE_KEY,
		rootProcessInstanceKey: PROCESS_INSTANCE_KEY,
		elementInstanceKey: null,
		jobKey: null,
		userTaskKey: null,
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
		inboundChannelType: null,
		inboundChannelToolName: null,
		...overrides,
	};
}

function createElementInstance(overrides: Partial<ElementInstance>): ElementInstance {
	return {
		processDefinitionId: 'loan',
		startDate: TIMESTAMP,
		endDate: null,
		elementId: 'loan',
		elementName: '',
		type: 'PROCESS',
		state: 'ACTIVE',
		hasIncident: false,
		tenantId: '<default>',
		elementInstanceKey: PROCESS_INSTANCE_KEY,
		processInstanceKey: PROCESS_INSTANCE_KEY,
		rootProcessInstanceKey: PROCESS_INSTANCE_KEY,
		processDefinitionKey: '1',
		incidentKey: null,
		...overrides,
	} as ElementInstance;
}

const elementInstances = [
	createElementInstance({}),
	createElementInstance({elementId: 'review', elementName: 'Review', type: 'SUB_PROCESS', elementInstanceKey: '200'}),
	createElementInstance({elementId: 'checkDocuments', elementName: '', type: 'USER_TASK', elementInstanceKey: '300'}),
	createElementInstance({
		elementId: 'scoring',
		elementName: 'Score applicant',
		type: 'SERVICE_TASK',
		elementInstanceKey: '400',
	}),
];

const userTasks = [
	{userTaskKey: '500', elementId: 'checkDocuments', elementInstanceKey: '300', name: null} as UserTask,
];

function getActivity(auditLogs: AuditLog[], documentVariableNames = new Set<string>()) {
	return getCaseActivity({auditLogs, model, elementInstances, userTasks, documentVariableNames});
}

describe('getCaseActivity', () => {
	it('should name the task and its stage of a task operation', () => {
		const [activity] = getActivity([
			createAuditLog({
				entityType: 'USER_TASK',
				operationType: 'ASSIGN',
				entityKey: '500',
				userTaskKey: '500',
				elementInstanceKey: '300',
				relatedEntityKey: 'jane',
			}),
		]);

		expect(activity).toMatchObject({
			kind: 'taskAssigned',
			elementName: 'Check documents',
			stageName: 'Review',
			assignee: 'jane',
		});
	});

	it('should list the variables that a task completion set on the completion', () => {
		const activities = getActivity([
			createAuditLog({
				auditLogKey: '3',
				entityType: 'USER_TASK',
				operationType: 'COMPLETE',
				userTaskKey: '500',
				elementInstanceKey: '300',
			}),
			createAuditLog({
				auditLogKey: '2',
				entityType: 'VARIABLE',
				operationType: 'UPDATE',
				elementInstanceKey: PROCESS_INSTANCE_KEY,
				entityDescription: 'documentsOk',
			}),
			createAuditLog({
				auditLogKey: '1',
				entityType: 'VARIABLE',
				operationType: 'CREATE',
				elementInstanceKey: PROCESS_INSTANCE_KEY,
				entityDescription: 'comment',
			}),
		]);

		expect(activities).toHaveLength(1);
		expect(activities[0]).toMatchObject({kind: 'taskCompleted', variableNames: ['comment', 'documentsOk']});
	});

	it('should group the variables that one request set', () => {
		const activities = getActivity([
			createAuditLog({
				auditLogKey: '2',
				entityType: 'VARIABLE',
				operationType: 'UPDATE',
				elementInstanceKey: PROCESS_INSTANCE_KEY,
				entityDescription: 'amount',
			}),
			createAuditLog({
				auditLogKey: '1',
				entityType: 'VARIABLE',
				operationType: 'UPDATE',
				elementInstanceKey: PROCESS_INSTANCE_KEY,
				entityDescription: 'applicant',
			}),
		]);

		expect(activities).toEqual([
			expect.objectContaining({key: '2', kind: 'dataChanged', stageName: null, variableNames: ['amount', 'applicant']}),
		]);
	});

	it('should tell document uploads and task drafts from other case data', () => {
		const activities = getActivity(
			[
				createAuditLog({
					auditLogKey: '2',
					entityType: 'VARIABLE',
					operationType: 'UPDATE',
					timestamp: LATER_TIMESTAMP,
					elementInstanceKey: PROCESS_INSTANCE_KEY,
					entityDescription: 'caseDocuments',
				}),
				createAuditLog({
					auditLogKey: '1',
					entityType: 'VARIABLE',
					operationType: 'CREATE',
					elementInstanceKey: '300',
					entityDescription: 'comment',
				}),
			],
			new Set(['caseDocuments']),
		);

		expect(activities).toEqual([
			expect.objectContaining({kind: 'documentsChanged', variableNames: ['caseDocuments']}),
			expect.objectContaining({kind: 'taskDraftSaved', elementName: 'Check documents', stageName: 'Review'}),
		]);
	});

	it('should name an automated step by its element, or by its job type', () => {
		const activities = getActivity([
			createAuditLog({
				auditLogKey: '2',
				entityType: 'JOB',
				operationType: 'COMPLETE',
				elementInstanceKey: '400',
				entityDescription: 'score',
			}),
			createAuditLog({
				auditLogKey: '1',
				entityType: 'JOB',
				operationType: 'COMPLETE',
				timestamp: LATER_TIMESTAMP,
				elementInstanceKey: '999',
				entityDescription: 'notify',
			}),
		]);

		expect(activities).toEqual([
			expect.objectContaining({kind: 'stepCompleted', elementName: 'Score applicant', stageName: 'Review'}),
			expect.objectContaining({kind: 'stepCompleted', elementName: 'notify', stageName: null}),
		]);
	});

	it('should keep case operations without an element, and leave out the others', () => {
		const activities = getActivity([
			createAuditLog({auditLogKey: '3', operationType: 'CANCEL', elementInstanceKey: PROCESS_INSTANCE_KEY}),
			createAuditLog({auditLogKey: '2', operationType: 'DELETE'}),
			createAuditLog({auditLogKey: '1', entityType: 'BATCH', operationType: 'CREATE'}),
		]);

		expect(activities).toEqual([expect.objectContaining({key: '3', kind: 'caseCancelled', elementName: null})]);
	});
});
