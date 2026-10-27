/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {batchOperationCreatedResultSchema} from './gen/zod/batchOperationCreatedResultSchema';
import {cancelProcessInstanceRequestSchema} from './gen/zod/cancelProcessInstanceRequestSchema';
import {createProcessInstanceResultSchema} from './gen/zod/createProcessInstanceResultSchema';
import {getProcessInstanceCallHierarchyStatus200Schema} from './gen/zod/getProcessInstanceCallHierarchySchema';
import {incidentSearchQueryResultSchema} from './gen/zod/incidentSearchQueryResultSchema';
import {incidentSearchQuerySchema} from './gen/zod/incidentSearchQuerySchema';
import {processInstanceCallHierarchyEntrySchema} from './gen/zod/processInstanceCallHierarchyEntrySchema';
import {processInstanceCreationInstructionSchema} from './gen/zod/processInstanceCreationInstructionSchema';
import {processInstanceElementStatisticsQueryResultSchema} from './gen/zod/processInstanceElementStatisticsQueryResultSchema';
import {processInstanceModificationInstructionSchema} from './gen/zod/processInstanceModificationInstructionSchema';
import {processInstanceResumptionBatchOperationRequestSchema} from './gen/zod/processInstanceResumptionBatchOperationRequestSchema';
import {processInstanceSearchQueryResultSchema} from './gen/zod/processInstanceSearchQueryResultSchema';
import {processInstanceSearchQuerySchema} from './gen/zod/processInstanceSearchQuerySchema';
import {processInstanceSequenceFlowResultSchema} from './gen/zod/processInstanceSequenceFlowResultSchema';
import {processInstanceSequenceFlowsQueryResultSchema} from './gen/zod/processInstanceSequenceFlowsQueryResultSchema';
import {processInstanceSuspensionBatchOperationRequestSchema} from './gen/zod/processInstanceSuspensionBatchOperationRequestSchema';
import {processInstanceWaitStateStatisticsQueryResultSchema} from './gen/zod/processInstanceWaitStateStatisticsQueryResultSchema';
import {processInstanceWaitStateStatisticsResultSchema} from './gen/zod/processInstanceWaitStateStatisticsResultSchema';
import {resumeProcessInstanceRequestSchema} from './gen/zod/resumeProcessInstanceRequestSchema';
import {stringFilterPropertySchema} from './gen/zod/stringFilterPropertySchema';
import {suspendProcessInstanceRequestSchema} from './gen/zod/suspendProcessInstanceRequestSchema';
import {variableValueFilterPropertySchema} from './gen/zod/variableValueFilterPropertySchema';
import type {BatchOperationCreatedResult} from './gen/types/BatchOperationCreatedResult';
import type {CancelProcessInstanceRequest} from './gen/types/CancelProcessInstanceRequest';
import type {CreateProcessInstanceResult} from './gen/types/CreateProcessInstanceResult';
import type {GetProcessInstanceCallHierarchyStatus200} from './gen/types/GetProcessInstanceCallHierarchy';
import type {IncidentSearchQuery} from './gen/types/IncidentSearchQuery';
import type {IncidentSearchQueryResult} from './gen/types/IncidentSearchQueryResult';
import type {ProcessInstanceCallHierarchyEntry} from './gen/types/ProcessInstanceCallHierarchyEntry';
import type {ProcessInstanceCancellationBatchOperationRequest} from './gen/types/ProcessInstanceCancellationBatchOperationRequest';
import type {ProcessInstanceCreationInstruction} from './gen/types/ProcessInstanceCreationInstruction';
import type {ProcessInstanceDeletionBatchOperationRequest} from './gen/types/ProcessInstanceDeletionBatchOperationRequest';
import type {ProcessInstanceElementStatisticsQueryResult} from './gen/types/ProcessInstanceElementStatisticsQueryResult';
import type {ProcessInstanceIncidentResolutionBatchOperationRequest} from './gen/types/ProcessInstanceIncidentResolutionBatchOperationRequest';
import type {ProcessInstanceMigrationBatchOperationRequest} from './gen/types/ProcessInstanceMigrationBatchOperationRequest';
import type {ProcessInstanceModificationBatchOperationRequest} from './gen/types/ProcessInstanceModificationBatchOperationRequest';
import type {ProcessInstanceModificationInstruction} from './gen/types/ProcessInstanceModificationInstruction';
import type {ProcessInstanceResumptionBatchOperationRequest} from './gen/types/ProcessInstanceResumptionBatchOperationRequest';
import type {ProcessInstanceSearchQuery} from './gen/types/ProcessInstanceSearchQuery';
import type {ProcessInstanceSearchQueryResult} from './gen/types/ProcessInstanceSearchQueryResult';
import type {ProcessInstanceSequenceFlowResult} from './gen/types/ProcessInstanceSequenceFlowResult';
import type {ProcessInstanceSequenceFlowsQueryResult} from './gen/types/ProcessInstanceSequenceFlowsQueryResult';
import type {ProcessInstanceSuspensionBatchOperationRequest} from './gen/types/ProcessInstanceSuspensionBatchOperationRequest';
import type {ProcessInstanceWaitStateStatisticsQueryResult} from './gen/types/ProcessInstanceWaitStateStatisticsQueryResult';
import type {ProcessInstanceWaitStateStatisticsResult} from './gen/types/ProcessInstanceWaitStateStatisticsResult';
import type {ResumeProcessInstanceRequest} from './gen/types/ResumeProcessInstanceRequest';
import type {StringFilterProperty} from './gen/types/StringFilterProperty';
import type {SuspendProcessInstanceRequest} from './gen/types/SuspendProcessInstanceRequest';
import type {VariableValueFilterProperty} from './gen/types/VariableValueFilterProperty';
import {
	processInstanceSchema,
	processInstanceStateSchema,
	type ProcessInstance,
	type ProcessInstanceState,
	type StatisticName,
} from './processes';

const processInstanceVariableValueFilterSchema = stringFilterPropertySchema;
type ProcessInstanceVariableValueFilter = StringFilterProperty;

const processInstanceVariableFilterSchema = variableValueFilterPropertySchema;
type ProcessInstanceVariableFilter = VariableValueFilterProperty;

const queryProcessInstancesRequestBodySchema = processInstanceSearchQuerySchema;
type QueryProcessInstancesRequestBody = ProcessInstanceSearchQuery;

const queryProcessInstancesResponseBodySchema = processInstanceSearchQueryResultSchema;
type QueryProcessInstancesResponseBody = ProcessInstanceSearchQueryResult;

const cancelProcessInstanceRequestBodySchema = cancelProcessInstanceRequestSchema;
type CancelProcessInstanceRequestBody = CancelProcessInstanceRequest;

const suspendProcessInstanceRequestBodySchema = suspendProcessInstanceRequestSchema;
type SuspendProcessInstanceRequestBody = SuspendProcessInstanceRequest;

const resumeProcessInstanceRequestBodySchema = resumeProcessInstanceRequestSchema;
type ResumeProcessInstanceRequestBody = ResumeProcessInstanceRequest;

const createProcessInstanceRequestBodySchema = processInstanceCreationInstructionSchema;
type CreateProcessInstanceRequestBody = ProcessInstanceCreationInstruction;

const createProcessInstanceResponseBodySchema = createProcessInstanceResultSchema;
type CreateProcessInstanceResponseBody = CreateProcessInstanceResult;

const queryProcessInstanceIncidentsRequestBodySchema = incidentSearchQuerySchema;
type QueryProcessInstanceIncidentsRequestBody = IncidentSearchQuery;

const queryProcessInstanceIncidentsResponseBodySchema = incidentSearchQueryResultSchema;
type QueryProcessInstanceIncidentsResponseBody = IncidentSearchQueryResult;

const callHierarchySchema = processInstanceCallHierarchyEntrySchema;
type CallHierarchy = ProcessInstanceCallHierarchyEntry;

const getProcessInstanceCallHierarchyResponseBodySchema = getProcessInstanceCallHierarchyStatus200Schema;
type GetProcessInstanceCallHierarchyResponseBody = GetProcessInstanceCallHierarchyStatus200;

const getProcessInstanceStatisticsResponseBodySchema = processInstanceElementStatisticsQueryResultSchema;
type GetProcessInstanceStatisticsResponseBody = ProcessInstanceElementStatisticsQueryResult;

const waitStateStatisticSchema = processInstanceWaitStateStatisticsResultSchema;
type WaitStateStatistic = ProcessInstanceWaitStateStatisticsResult;

const getProcessInstanceWaitStateStatisticsResponseBodySchema = processInstanceWaitStateStatisticsQueryResultSchema;
type GetProcessInstanceWaitStateStatisticsResponseBody = ProcessInstanceWaitStateStatisticsQueryResult;

const sequenceFlowSchema = processInstanceSequenceFlowResultSchema;
type SequenceFlow = ProcessInstanceSequenceFlowResult;

const getProcessInstanceSequenceFlowsResponseBodySchema = processInstanceSequenceFlowsQueryResultSchema;
type GetProcessInstanceSequenceFlowsResponseBody = ProcessInstanceSequenceFlowsQueryResult;

type CreateIncidentResolutionBatchOperationRequestBody = ProcessInstanceIncidentResolutionBatchOperationRequest;

const createIncidentResolutionBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateIncidentResolutionBatchOperationResponseBody = BatchOperationCreatedResult;

type CreateCancellationBatchOperationRequestBody = ProcessInstanceCancellationBatchOperationRequest;

const createCancellationBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateCancellationBatchOperationResponseBody = BatchOperationCreatedResult;

const suspendProcessInstancesBatchOperationRequestBodySchema = processInstanceSuspensionBatchOperationRequestSchema;
type SuspendProcessInstancesBatchOperationRequestBody = ProcessInstanceSuspensionBatchOperationRequest;

const suspendProcessInstancesBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type SuspendProcessInstancesBatchOperationResponseBody = BatchOperationCreatedResult;

const resumeProcessInstancesBatchOperationRequestBodySchema = processInstanceResumptionBatchOperationRequestSchema;
type ResumeProcessInstancesBatchOperationRequestBody = ProcessInstanceResumptionBatchOperationRequest;

const resumeProcessInstancesBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type ResumeProcessInstancesBatchOperationResponseBody = BatchOperationCreatedResult;

type CreateDeletionBatchOperationRequestBody = ProcessInstanceDeletionBatchOperationRequest;

const createDeletionBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateDeletionBatchOperationResponseBody = BatchOperationCreatedResult;

type CreateMigrationBatchOperationRequestBody = ProcessInstanceMigrationBatchOperationRequest;

const createMigrationBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateMigrationBatchOperationResponseBody = BatchOperationCreatedResult;

type CreateModificationBatchOperationRequestBody = ProcessInstanceModificationBatchOperationRequest;

const createModificationBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateModificationBatchOperationResponseBody = BatchOperationCreatedResult;

// Legacy copy of the modification body. `index.ts` exports the one from `process-instance-commands.ts`.
const modifyProcessInstanceRequestBodySchema = processInstanceModificationInstructionSchema;
type ModifyProcessInstanceRequestBody = ProcessInstanceModificationInstruction;

const resolveProcessInstanceIncidentsResponseBodySchema = batchOperationCreatedResultSchema;
type ResolveProcessInstanceIncidentsResponseBody = BatchOperationCreatedResult;

const getProcessInstance = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const createProcessInstance = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances` as const,
} as const satisfies Endpoint;

const queryProcessInstances = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/search` as const,
} as const satisfies Endpoint;

const cancelProcessInstance = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/cancellation` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const suspendProcessInstance = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/suspension` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const resumeProcessInstance = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/resumption` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const queryProcessInstanceIncidents = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/incidents/search` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const getProcessInstanceCallHierarchy = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/call-hierarchy` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const getProcessInstanceStatistics = {
	method: 'GET',
	getUrl: ({processInstanceKey, statisticName = 'element-instances'}) =>
		`/${API_VERSION}/process-instances/${processInstanceKey}/statistics/${statisticName}` as const,
} as const satisfies Endpoint<GetProcessInstanceStatisticsParams>;

type GetProcessInstanceStatisticsParams = Pick<ProcessInstance, 'processInstanceKey'> & {
	statisticName: StatisticName;
};

const getProcessInstanceWaitStateStatistics = {
	method: 'GET',
	getUrl: ({processInstanceKey}) =>
		`/${API_VERSION}/process-instances/${processInstanceKey}/statistics/wait-states` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const getProcessInstanceSequenceFlows = {
	method: 'GET',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/sequence-flows` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const createIncidentResolutionBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/incident-resolution` as const,
} as const satisfies Endpoint;

const createCancellationBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/cancellation` as const,
} as const satisfies Endpoint;

const suspendProcessInstancesBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/suspension` as const,
} as const satisfies Endpoint;

const resumeProcessInstancesBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/resumption` as const,
} as const satisfies Endpoint;

const createDeletionBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/deletion` as const,
} as const satisfies Endpoint;

const createMigrationBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/migration` as const,
} as const satisfies Endpoint;

const createModificationBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-instances/modification` as const,
} as const satisfies Endpoint;

const modifyProcessInstance = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/modification` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const resolveProcessInstanceIncidents = {
	method: 'POST',
	getUrl: ({processInstanceKey}) =>
		`/${API_VERSION}/process-instances/${processInstanceKey}/incident-resolution` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

export {
	createProcessInstance,
	getProcessInstance,
	queryProcessInstances,
	cancelProcessInstance,
	suspendProcessInstance,
	resumeProcessInstance,
	queryProcessInstanceIncidents,
	getProcessInstanceCallHierarchy,
	getProcessInstanceStatistics,
	getProcessInstanceWaitStateStatistics,
	getProcessInstanceSequenceFlows,
	createIncidentResolutionBatchOperation,
	createCancellationBatchOperation,
	suspendProcessInstancesBatchOperation,
	resumeProcessInstancesBatchOperation,
	createDeletionBatchOperation,
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
	suspendProcessInstanceRequestBodySchema,
	resumeProcessInstanceRequestBodySchema,
	queryProcessInstanceIncidentsRequestBodySchema,
	queryProcessInstanceIncidentsResponseBodySchema,
	getProcessInstanceCallHierarchyResponseBodySchema,
	getProcessInstanceStatisticsResponseBodySchema,
	getProcessInstanceWaitStateStatisticsResponseBodySchema,
	waitStateStatisticSchema,
	getProcessInstanceSequenceFlowsResponseBodySchema,
	createIncidentResolutionBatchOperationResponseBodySchema,
	createCancellationBatchOperationResponseBodySchema,
	suspendProcessInstancesBatchOperationRequestBodySchema,
	suspendProcessInstancesBatchOperationResponseBodySchema,
	resumeProcessInstancesBatchOperationRequestBodySchema,
	resumeProcessInstancesBatchOperationResponseBodySchema,
	createDeletionBatchOperationResponseBodySchema,
	createMigrationBatchOperationResponseBodySchema,
	createModificationBatchOperationResponseBodySchema,
	resolveProcessInstanceIncidentsResponseBodySchema,
	processInstanceStateSchema,
	processInstanceSchema,
	sequenceFlowSchema,
	callHierarchySchema,
	processInstanceVariableFilterSchema,
	processInstanceVariableValueFilterSchema,
};

export type {
	CreateProcessInstanceRequestBody,
	CreateProcessInstanceResponseBody,
	QueryProcessInstancesRequestBody,
	QueryProcessInstancesResponseBody,
	CancelProcessInstanceRequestBody,
	SuspendProcessInstanceRequestBody,
	ResumeProcessInstanceRequestBody,
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
	GetProcessInstanceWaitStateStatisticsResponseBody,
	WaitStateStatistic,
	CreateIncidentResolutionBatchOperationRequestBody,
	CreateIncidentResolutionBatchOperationResponseBody,
	CreateCancellationBatchOperationRequestBody,
	CreateCancellationBatchOperationResponseBody,
	SuspendProcessInstancesBatchOperationRequestBody,
	SuspendProcessInstancesBatchOperationResponseBody,
	ResumeProcessInstancesBatchOperationRequestBody,
	ResumeProcessInstancesBatchOperationResponseBody,
	CreateDeletionBatchOperationRequestBody,
	CreateDeletionBatchOperationResponseBody,
	CreateMigrationBatchOperationRequestBody,
	CreateMigrationBatchOperationResponseBody,
	CreateModificationBatchOperationRequestBody,
	CreateModificationBatchOperationResponseBody,
	ModifyProcessInstanceRequestBody,
	ResolveProcessInstanceIncidentsResponseBody,
	ProcessInstanceVariableFilter,
	ProcessInstanceVariableValueFilter,
};
