/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	API_VERSION,
	getCollectionResponseBodySchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	advancedDateTimeFilterSchema,
	advancedStringFilterSchema,
	advancedIntegerFilterSchema,
	basicStringFilterSchema,
	getOrFilterSchema,
	type Endpoint,
} from './common';
import {variableSchema} from './variable';
import {
	processInstanceSchema as baseProcessInstanceSchema,
	processInstanceStateSchema,
	processDefinitionStatisticSchema,
	type ProcessInstance,
	type ProcessInstanceState,
	type StatisticName,
} from './processes';
import {batchOperationTypeSchema} from './batch-operation';
import {queryIncidentsRequestBodySchema, queryIncidentsResponseBodySchema} from './incident';

const processInstanceSchema = baseProcessInstanceSchema.extend({
	processInstanceKey: z.string(),
	processDefinitionKey: z.string(),
	parentProcessInstanceKey: z.string(),
	parentElementInstanceKey: z.string(),
});

const processInstanceBatchOperationTypeSchema = z.union([
	batchOperationTypeSchema,
	z.literal('DELETE_DECISION_INSTANCE'),
]);

const processInstanceVariableFilterSchema = z.object({
	name: z.string(),
	value: advancedStringFilterSchema,
});

const advancedProcessInstanceStateFilterSchema = z
	.object({
		$eq: processInstanceStateSchema,
		$neq: processInstanceStateSchema,
		$exists: z.boolean(),
		$in: z.array(processInstanceStateSchema),
		$like: z.string(),
	})
	.partial();

const queryProcessInstancesFilterSchema = z
	.object({
		processDefinitionId: advancedStringFilterSchema,
		processDefinitionName: advancedStringFilterSchema,
		processDefinitionVersion: advancedIntegerFilterSchema,
		processDefinitionVersionTag: advancedStringFilterSchema,
		processDefinitionKey: basicStringFilterSchema,
		processInstanceKey: basicStringFilterSchema,
		parentProcessInstanceKey: basicStringFilterSchema,
		parentElementInstanceKey: basicStringFilterSchema,
		startDate: advancedDateTimeFilterSchema,
		endDate: advancedDateTimeFilterSchema,
		state: advancedProcessInstanceStateFilterSchema,
		hasIncident: z.boolean(),
		tenantId: advancedStringFilterSchema,
		variables: z.array(processInstanceVariableFilterSchema),
		batchOperationId: advancedStringFilterSchema,
		errorMessage: advancedStringFilterSchema,
		hasRetriesLeft: z.boolean(),
		elementInstanceState: advancedProcessInstanceStateFilterSchema,
		elementId: advancedStringFilterSchema,
		hasElementInstanceIncident: z.boolean(),
		incidentErrorHashCode: advancedIntegerFilterSchema,
		tags: z
			.array(
				z
					.string()
					.min(1)
					.max(100)
					.regex(/^[A-Za-z][A-Za-z0-9_\-:.]{0,99}$/),
			)
			.max(10),
	})
	.partial();

const queryProcessInstancesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'processInstanceKey',
		'processDefinitionId',
		'processDefinitionName',
		'processDefinitionVersion',
		'processDefinitionVersionTag',
		'processDefinitionKey',
		'parentProcessInstanceKey',
		'parentElementInstanceKey',
		'startDate',
		'endDate',
		'state',
		'hasIncident',
		'tenantId',
	] as const,
	filter: getOrFilterSchema(queryProcessInstancesFilterSchema),
});
type QueryProcessInstancesRequestBody = z.infer<typeof queryProcessInstancesRequestBodySchema>;

const queryProcessInstancesResponseBodySchema = getQueryResponseBodySchema(processInstanceSchema);
type QueryProcessInstancesResponseBody = z.infer<typeof queryProcessInstancesResponseBodySchema>;

const cancelProcessInstanceRequestBodySchema = z
	.object({
		operationReference: z.number().int(),
	})
	.optional();
type CancelProcessInstanceRequestBody = z.infer<typeof cancelProcessInstanceRequestBodySchema>;

const getProcessInstance: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}`,
};

const createProcessInstanceRequestBodySchema = z.object({
	variables: z.record(z.string(), variableSchema).optional(),
	operationReference: z.number().optional(),
	startInstructions: z.array(z.string()).optional(),
	awaitCompletion: z.boolean().optional(),
	fetchVariables: z.array(z.string()).optional(),
	requestTimeout: z.number().optional(),
	...processInstanceSchema
		.pick({
			processDefinitionId: true,
			processDefinitionVersion: true,
			tenantId: true,
			processDefinitionKey: true,
		})
		.partial().shape,
});

type CreateProcessInstanceRequestBody = z.infer<typeof createProcessInstanceRequestBodySchema>;

const createProcessInstanceResponseBodySchema = z.object({
	variables: z.record(z.string(), variableSchema).nullable(),
	...processInstanceSchema.pick({
		processDefinitionId: true,
		processDefinitionVersion: true,
		tenantId: true,
		processDefinitionKey: true,
		processInstanceKey: true,
	}).shape,
});

type CreateProcessInstanceResponseBody = z.infer<typeof createProcessInstanceResponseBodySchema>;

const createProcessInstance: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances`,
};

const queryProcessInstances: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/search`,
};

const cancelProcessInstance: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/cancellation`,
};

const queryProcessInstanceIncidentsRequestBodySchema = queryIncidentsRequestBodySchema;
type QueryProcessInstanceIncidentsRequestBody = z.infer<typeof queryProcessInstanceIncidentsRequestBodySchema>;

const queryProcessInstanceIncidentsResponseBodySchema = queryIncidentsResponseBodySchema;
type QueryProcessInstanceIncidentsResponseBody = z.infer<typeof queryProcessInstanceIncidentsResponseBodySchema>;

const queryProcessInstanceIncidents: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/incidents/search`,
};

const getProcessInstanceCallHierarchy: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/call-hierarchy`,
};

const callHierarchySchema = z.object({
	processInstanceKey: z.string(),
	processDefinitionKey: z.string(),
	processDefinitionName: z.string(),
});
type CallHierarchy = z.infer<typeof callHierarchySchema>;
const getProcessInstanceCallHierarchyResponseBodySchema = z.array(callHierarchySchema);
type GetProcessInstanceCallHierarchyResponseBody = z.infer<typeof getProcessInstanceCallHierarchyResponseBodySchema>;

const getProcessInstanceStatistics: Endpoint<GetProcessInstanceStatisticsParams> = {
	method: 'GET',
	getUrl: ({processInstanceKey, statisticName = 'element-instances'}) =>
		`/${API_VERSION}/process-instances/${processInstanceKey}/statistics/${statisticName}`,
};

const getProcessInstanceStatisticsResponseBodySchema = getCollectionResponseBodySchema(
	processDefinitionStatisticSchema,
);
type GetProcessInstanceStatisticsResponseBody = z.infer<typeof getProcessInstanceStatisticsResponseBodySchema>;

type GetProcessInstanceStatisticsParams = Pick<ProcessInstance, 'processInstanceKey'> & {
	statisticName: StatisticName;
};

const getProcessInstanceSequenceFlows: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/sequence-flows`,
};

const sequenceFlowSchema = z.object({
	processInstanceKey: z.string(),
	sequenceFlowId: z.string(),
	processDefinitionKey: z.string(),
	processDefinitionId: z.string(),
	elementId: z.string(),
	tenantId: z.string(),
});
type SequenceFlow = z.infer<typeof sequenceFlowSchema>;

const getProcessInstanceSequenceFlowsResponseBodySchema = getCollectionResponseBodySchema(sequenceFlowSchema);
type GetProcessInstanceSequenceFlowsResponseBody = z.infer<typeof getProcessInstanceSequenceFlowsResponseBodySchema>;

const createIncidentResolutionBatchOperationRequestBodySchema = z.object({
	filter: getOrFilterSchema(queryProcessInstancesFilterSchema),
});

type CreateIncidentResolutionBatchOperationRequestBody = z.infer<
	typeof createIncidentResolutionBatchOperationRequestBodySchema
>;

const createIncidentResolutionBatchOperationResponseBodySchema = z.object({
	batchOperationKey: z.string(),
	batchOperationType: processInstanceBatchOperationTypeSchema,
});

type CreateIncidentResolutionBatchOperationResponseBody = z.infer<
	typeof createIncidentResolutionBatchOperationResponseBodySchema
>;

const createIncidentResolutionBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/incident-resolution`,
};

const createCancellationBatchOperationRequestBodySchema = z.object({
	filter: getOrFilterSchema(queryProcessInstancesFilterSchema),
});

type CreateCancellationBatchOperationRequestBody = z.infer<typeof createCancellationBatchOperationRequestBodySchema>;

const createCancellationBatchOperationResponseBodySchema = z.object({
	batchOperationKey: z.string(),
	batchOperationType: processInstanceBatchOperationTypeSchema,
});

type CreateCancellationBatchOperationResponseBody = z.infer<typeof createCancellationBatchOperationResponseBodySchema>;

const createCancellationBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/cancellation`,
};

const createMigrationBatchOperationRequestBodySchema = z.object({
	filter: getOrFilterSchema(queryProcessInstancesFilterSchema),
	migrationPlan: z.object({
		mappingInstructions: z.array(
			z.object({
				sourceElementId: z.string(),
				targetElementId: z.string(),
			}),
		),
		targetProcessDefinitionKey: z.string(),
	}),
});

type CreateMigrationBatchOperationRequestBody = z.infer<typeof createMigrationBatchOperationRequestBodySchema>;

const createMigrationBatchOperationResponseBodySchema = z.object({
	batchOperationKey: z.string(),
	batchOperationType: processInstanceBatchOperationTypeSchema,
});

type CreateMigrationBatchOperationResponseBody = z.infer<typeof createMigrationBatchOperationResponseBodySchema>;

const createMigrationBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/migration`,
};

const createModificationBatchOperationRequestBodySchema = z.object({
	filter: getOrFilterSchema(queryProcessInstancesFilterSchema),
	moveInstructions: z.array(
		z.object({
			sourceElementId: z.string(),
			targetElementId: z.string(),
		}),
	),
});

type CreateModificationBatchOperationRequestBody = z.infer<typeof createModificationBatchOperationRequestBodySchema>;

const createModificationBatchOperationResponseBodySchema = z.object({
	batchOperationKey: z.string(),
	batchOperationType: processInstanceBatchOperationTypeSchema,
});

type CreateModificationBatchOperationResponseBody = z.infer<typeof createModificationBatchOperationResponseBodySchema>;

const createModificationBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/modification`,
};

const variableInstructionSchema = z.object({
	variables: z.record(z.string(), z.unknown()),
	scopeId: z.string().optional(),
});
const activateInstructionSchema = z.object({
	elementId: z.string(),
	variableInstructions: z.array(variableInstructionSchema).optional(),
	ancestorElementInstanceKey: z.string().optional(),
});
const terminateInstructionSchema = z.object({
	elementInstanceKey: z.string(),
});

const modifyProcessInstanceRequestBodySchema = z
	.object({
		operationReference: z.number().optional(),
		activateInstructions: z.array(activateInstructionSchema).optional(),
		terminateInstructions: z.array(terminateInstructionSchema).optional(),
	})
	.refine(
		({activateInstructions, terminateInstructions}) =>
			(activateInstructions !== undefined && activateInstructions.length > 0) ||
			(terminateInstructions !== undefined && terminateInstructions.length > 0),
	);
type ModifyProcessInstanceRequestBody = z.infer<typeof modifyProcessInstanceRequestBodySchema>;

const modifyProcessInstance: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/modification`,
};

const resolveProcessInstanceIncidentsResponseBodySchema = z.object({
	batchOperationKey: z.string(),
	batchOperationType: processInstanceBatchOperationTypeSchema,
});

type ResolveProcessInstanceIncidentsResponseBody = z.infer<typeof resolveProcessInstanceIncidentsResponseBodySchema>;

const resolveProcessInstanceIncidents: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/incident-resolution`,
};

export {
	createProcessInstance,
	getProcessInstance,
	queryProcessInstances,
	cancelProcessInstance,
	queryProcessInstanceIncidents,
	getProcessInstanceCallHierarchy,
	getProcessInstanceStatistics,
	getProcessInstanceSequenceFlows,
	createIncidentResolutionBatchOperation,
	createCancellationBatchOperation,
	createMigrationBatchOperation,
	createModificationBatchOperation,
	modifyProcessInstance,
	resolveProcessInstanceIncidents,
	createProcessInstanceRequestBodySchema,
	createProcessInstanceResponseBodySchema,
	modifyProcessInstanceRequestBodySchema,
	queryProcessInstancesRequestBodySchema,
	queryProcessInstancesResponseBodySchema,
	cancelProcessInstanceRequestBodySchema,
	queryProcessInstanceIncidentsRequestBodySchema,
	queryProcessInstanceIncidentsResponseBodySchema,
	getProcessInstanceCallHierarchyResponseBodySchema,
	getProcessInstanceStatisticsResponseBodySchema,
	getProcessInstanceSequenceFlowsResponseBodySchema,
	createIncidentResolutionBatchOperationResponseBodySchema,
	createCancellationBatchOperationResponseBodySchema,
	createMigrationBatchOperationResponseBodySchema,
	createModificationBatchOperationResponseBodySchema,
	resolveProcessInstanceIncidentsResponseBodySchema,
	processInstanceStateSchema,
	processInstanceSchema,
	sequenceFlowSchema,
	callHierarchySchema,
};
export type {
	CreateProcessInstanceRequestBody,
	CreateProcessInstanceResponseBody,
	QueryProcessInstancesRequestBody,
	QueryProcessInstancesResponseBody,
	CancelProcessInstanceRequestBody,
	QueryProcessInstanceIncidentsRequestBody,
	QueryProcessInstanceIncidentsResponseBody,
	CallHierarchy,
	GetProcessInstanceCallHierarchyResponseBody,
	SequenceFlow,
	GetProcessInstanceSequenceFlowsResponseBody,
	ProcessInstanceState,
	StatisticName,
	ProcessInstance,
	GetProcessInstanceStatisticsResponseBody,
	CreateIncidentResolutionBatchOperationRequestBody,
	CreateIncidentResolutionBatchOperationResponseBody,
	CreateCancellationBatchOperationRequestBody,
	CreateCancellationBatchOperationResponseBody,
	CreateMigrationBatchOperationRequestBody,
	CreateMigrationBatchOperationResponseBody,
	CreateModificationBatchOperationRequestBody,
	CreateModificationBatchOperationResponseBody,
	ModifyProcessInstanceRequestBody,
	ResolveProcessInstanceIncidentsResponseBody,
};
