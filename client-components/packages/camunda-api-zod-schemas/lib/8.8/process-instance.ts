/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {queryIncidentsRequestBodySchema, queryIncidentsResponseBodySchema} from './incident';
import {
	cancelProcessInstanceRequestSchema,
	processInstanceCreationInstructionSchema,
	createProcessInstanceResultSchema,
	processInstanceCallHierarchyEntrySchema,
	processInstanceSequenceFlowResultSchema,
	processInstanceSequenceFlowsQueryResultSchema,
	processInstanceSearchQuerySchema,
	processInstanceSearchQueryResultSchema,
	processInstanceCancellationBatchOperationRequestSchema,
	processInstanceIncidentResolutionBatchOperationRequestSchema,
	processInstanceMigrationBatchOperationRequestSchema,
	processInstanceModificationBatchOperationRequestSchema,
	processInstanceModificationInstructionSchema,
	processInstanceMigrationInstructionSchema,
	batchOperationCreatedResultSchema,
	getProcessInstanceCallHierarchy200Schema,
	getProcessInstanceStatistics200Schema,
	processInstanceResultSchema,
	processInstanceStateEnumSchema,
} from './gen';

const processInstanceSchema = processInstanceResultSchema;
type ProcessInstance = z.infer<typeof processInstanceSchema>;
const processInstanceStateSchema = processInstanceStateEnumSchema;
type ProcessInstanceState = z.infer<typeof processInstanceStateSchema>;

const queryProcessInstancesRequestBodySchema = processInstanceSearchQuerySchema;
type QueryProcessInstancesRequestBody = z.infer<typeof queryProcessInstancesRequestBodySchema>;

const queryProcessInstancesResponseBodySchema = processInstanceSearchQueryResultSchema;
type QueryProcessInstancesResponseBody = z.infer<typeof queryProcessInstancesResponseBodySchema>;

const cancelProcessInstanceRequestBodySchema = cancelProcessInstanceRequestSchema;
type CancelProcessInstanceRequestBody = z.infer<typeof cancelProcessInstanceRequestBodySchema>;

const getProcessInstance: Endpoint<{processInstanceKey: string}> = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}`,
};

const createProcessInstanceRequestBodySchema = processInstanceCreationInstructionSchema;
type CreateProcessInstanceRequestBody = z.infer<typeof createProcessInstanceRequestBodySchema>;

const createProcessInstanceResponseBodySchema = createProcessInstanceResultSchema;
type CreateProcessInstanceResponseBody = z.infer<typeof createProcessInstanceResponseBodySchema>;

const createProcessInstance: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances`,
};

const queryProcessInstances: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/search`,
};

const cancelProcessInstance: Endpoint<{processInstanceKey: string}> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/cancellation`,
};

const queryProcessInstanceIncidentsRequestBodySchema = queryIncidentsRequestBodySchema;
type QueryProcessInstanceIncidentsRequestBody = z.infer<typeof queryProcessInstanceIncidentsRequestBodySchema>;

const queryProcessInstanceIncidentsResponseBodySchema = queryIncidentsResponseBodySchema;
type QueryProcessInstanceIncidentsResponseBody = z.infer<typeof queryProcessInstanceIncidentsResponseBodySchema>;

const queryProcessInstanceIncidents: Endpoint<{processInstanceKey: string}> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/incidents/search`,
};

const getProcessInstanceCallHierarchy: Endpoint<{processInstanceKey: string}> = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/call-hierarchy`,
};

const callHierarchySchema = processInstanceCallHierarchyEntrySchema;
type CallHierarchy = z.infer<typeof callHierarchySchema>;
const getProcessInstanceCallHierarchyResponseBodySchema = getProcessInstanceCallHierarchy200Schema;
type GetProcessInstanceCallHierarchyResponseBody = z.infer<typeof getProcessInstanceCallHierarchyResponseBodySchema>;

const getProcessInstanceStatistics: Endpoint<GetProcessInstanceStatisticsParams> = {
	method: 'GET',
	getUrl: ({processInstanceKey, statisticName = 'element-instances'}) =>
		`/${API_VERSION}/process-instances/${processInstanceKey}/statistics/${statisticName}`,
};

const getProcessInstanceStatisticsResponseBodySchema = getProcessInstanceStatistics200Schema;
type GetProcessInstanceStatisticsResponseBody = z.infer<typeof getProcessInstanceStatisticsResponseBodySchema>;

type GetProcessInstanceStatisticsParams = {processInstanceKey: string} & {
	statisticName: 'element-instances';
};

const getProcessInstanceSequenceFlows: Endpoint<{processInstanceKey: string}> = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/sequence-flows`,
};

const sequenceFlowSchema = processInstanceSequenceFlowResultSchema;
type SequenceFlow = z.infer<typeof sequenceFlowSchema>;

const getProcessInstanceSequenceFlowsResponseBodySchema = processInstanceSequenceFlowsQueryResultSchema;
type GetProcessInstanceSequenceFlowsResponseBody = z.infer<typeof getProcessInstanceSequenceFlowsResponseBodySchema>;

const createIncidentResolutionBatchOperationRequestBodySchema =
	processInstanceIncidentResolutionBatchOperationRequestSchema;
type CreateIncidentResolutionBatchOperationRequestBody = z.infer<
	typeof createIncidentResolutionBatchOperationRequestBodySchema
>;

const createIncidentResolutionBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateIncidentResolutionBatchOperationResponseBody = z.infer<
	typeof createIncidentResolutionBatchOperationResponseBodySchema
>;

const createIncidentResolutionBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/incident-resolution`,
};

const createCancellationBatchOperationRequestBodySchema = processInstanceCancellationBatchOperationRequestSchema;
type CreateCancellationBatchOperationRequestBody = z.infer<typeof createCancellationBatchOperationRequestBodySchema>;

const createCancellationBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateCancellationBatchOperationResponseBody = z.infer<typeof createCancellationBatchOperationResponseBodySchema>;

const createCancellationBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/cancellation`,
};

const createMigrationBatchOperationRequestBodySchema = processInstanceMigrationBatchOperationRequestSchema;
type CreateMigrationBatchOperationRequestBody = z.infer<typeof createMigrationBatchOperationRequestBodySchema>;

const createMigrationBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateMigrationBatchOperationResponseBody = z.infer<typeof createMigrationBatchOperationResponseBodySchema>;

const createMigrationBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/migration`,
};

const createModificationBatchOperationRequestBodySchema = processInstanceModificationBatchOperationRequestSchema;
type CreateModificationBatchOperationRequestBody = z.infer<typeof createModificationBatchOperationRequestBodySchema>;

const createModificationBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateModificationBatchOperationResponseBody = z.infer<typeof createModificationBatchOperationResponseBodySchema>;

const createModificationBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/modification`,
};

const modifyProcessInstanceRequestBodySchema = processInstanceModificationInstructionSchema;
type ModifyProcessInstanceRequestBody = z.infer<typeof modifyProcessInstanceRequestBodySchema>;

const modifyProcessInstance: Endpoint<{processInstanceKey: string}> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/modification`,
};

const migrateProcessInstanceRequestBodySchema = processInstanceMigrationInstructionSchema;
type MigrateProcessInstanceRequestBody = z.infer<typeof migrateProcessInstanceRequestBodySchema>;

const migrateProcessInstance: Endpoint<{processInstanceKey: string}> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/migration`,
};

const resolveProcessInstanceIncidentsResponseBodySchema = batchOperationCreatedResultSchema;
type ResolveProcessInstanceIncidentsResponseBody = z.infer<typeof resolveProcessInstanceIncidentsResponseBodySchema>;

const resolveProcessInstanceIncidents: Endpoint<{processInstanceKey: string}> = {
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
	migrateProcessInstance,
	resolveProcessInstanceIncidents,
	createProcessInstanceRequestBodySchema,
	createProcessInstanceResponseBodySchema,
	modifyProcessInstanceRequestBodySchema,
	migrateProcessInstanceRequestBodySchema,
	queryProcessInstancesRequestBodySchema,
	queryProcessInstancesResponseBodySchema,
	cancelProcessInstanceRequestBodySchema,
	queryProcessInstanceIncidentsRequestBodySchema,
	queryProcessInstanceIncidentsResponseBodySchema,
	getProcessInstanceCallHierarchyResponseBodySchema,
	getProcessInstanceStatisticsResponseBodySchema,
	getProcessInstanceSequenceFlowsResponseBodySchema,
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
	MigrateProcessInstanceRequestBody,
	ResolveProcessInstanceIncidentsResponseBody,
};
