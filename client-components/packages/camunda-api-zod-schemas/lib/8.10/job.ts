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
	advancedStringFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	getEnumFilterSchema,
	type Endpoint,
	basicStringFilterSchema,
} from './common';

const jobStateSchema = z.enum([
	'CREATED',
	'COMPLETED',
	'FAILED',
	'RETRIES_UPDATED',
	'TIMED_OUT',
	'CANCELED',
	'ERROR_THROWN',
	'MIGRATED',
]);
type JobState = z.infer<typeof jobStateSchema>;

const jobKindSchema = z.enum(['BPMN_ELEMENT', 'EXECUTION_LISTENER', 'TASK_LISTENER', 'AD_HOC_SUB_PROCESS']);
type JobKind = z.infer<typeof jobKindSchema>;

const listenerEventTypeSchema = z.enum([
	'UNSPECIFIED',
	'START',
	'END',
	'CREATING',
	'ASSIGNING',
	'UPDATING',
	'COMPLETING',
	'CANCELING',
]);
type ListenerEventType = z.infer<typeof listenerEventTypeSchema>;

const jobStateFilterSchema = getEnumFilterSchema(jobStateSchema);
const jobKindFilterSchema = getEnumFilterSchema(jobKindSchema);
const listenerEventTypeFilterSchema = getEnumFilterSchema(listenerEventTypeSchema);

const jobSchema = z.object({
	jobKey: z.string(),
	type: z.string(),
	worker: z.string(),
	state: jobStateSchema,
	kind: jobKindSchema,
	listenerEventType: listenerEventTypeSchema,
	retries: z.number(),
	isDenied: z.boolean().nullable(),
	deniedReason: z.string().nullable(),
	hasFailedWithRetriesLeft: z.boolean(),
	errorCode: z.string().nullable(),
	errorMessage: z.string().nullable(),
	customHeaders: z.record(z.string(), z.unknown()).nullable(),
	deadline: z.string().nullable(),
	endTime: z.string().nullable(),
	processDefinitionId: z.string(),
	processDefinitionKey: z.string(),
	processInstanceKey: z.string(),
	rootProcessInstanceKey: z.string().nullable(),
	elementId: z.string(),
	elementInstanceKey: z.string(),
	creationTime: z.string().nullable(),
	lastUpdateTime: z.string().nullable(),
	tags: z.array(z.string()),
	tenantId: z.string(),
});
type Job = z.infer<typeof jobSchema>;

const queryJobsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'jobKey',
		'type',
		'worker',
		'state',
		'kind',
		'listenerEventType',
		'retries',
		'isDenied',
		'deniedReason',
		'hasFailedWithRetriesLeft',
		'errorCode',
		'errorMessage',
		'customHeaders',
		'deadline',
		'endTime',
		'processDefinitionId',
		'processDefinitionKey',
		'processInstanceKey',
		'elementId',
		'elementInstanceKey',
		'tenantId',
	] as const,
	filter: z.object({
		jobKey: basicStringFilterSchema.optional(),
		type: advancedStringFilterSchema.optional(),
		worker: advancedStringFilterSchema.optional(),
		state: jobStateFilterSchema.optional(),
		kind: jobKindFilterSchema.optional(),
		listenerEventType: listenerEventTypeFilterSchema.optional(),
		processDefinitionId: advancedStringFilterSchema.optional(),
		processDefinitionKey: basicStringFilterSchema.optional(),
		processInstanceKey: basicStringFilterSchema.optional(),
		elementId: advancedStringFilterSchema.optional(),
		elementInstanceKey: basicStringFilterSchema.optional(),
		tenantId: advancedStringFilterSchema.optional(),
	}),
});

type QueryJobsRequestBody = z.infer<typeof queryJobsRequestBodySchema>;

const queryJobs: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/search`;
	},
};

const queryJobsResponseBodySchema = getQueryResponseBodySchema(jobSchema);
type QueryJobsResponseBody = z.infer<typeof queryJobsResponseBodySchema>;

const activateJobsRequestBodySchema = z.object({
	type: z.string(),
	worker: z.string().optional(),
	timeout: z.number(),
	maxJobsToActivate: z.number(),
	fetchVariable: z.array(z.string()).optional(),
	requestTimeout: z.number().optional(),
	tenantIds: z.array(z.string()).optional(),
});
type ActivateJobsRequestBody = z.infer<typeof activateJobsRequestBodySchema>;

const activatedJobSchema = z.object({
	type: z.string(),
	processDefinitionId: z.string(),
	processDefinitionVersion: z.number(),
	elementId: z.string(),
	customHeaders: z.record(z.string(), z.unknown()).nullable(),
	worker: z.string(),
	retries: z.number(),
	deadline: z.number(),
	variables: z.record(z.string(), z.unknown()).nullable(),
	tenantId: z.string(),
	jobKey: z.string(),
	processInstanceKey: z.string(),
	processDefinitionKey: z.string(),
	elementInstanceKey: z.string(),
	kind: jobKindSchema,
	listenerEventType: listenerEventTypeSchema,
	rootProcessInstanceKey: z.string().nullable(),
	userTask: z.unknown().nullable(),
	tags: z.array(z.string()),
});
type ActivatedJob = z.infer<typeof activatedJobSchema>;

const activateJobsResponseBodySchema = z.object({
	jobs: z.array(activatedJobSchema),
});
type ActivateJobsResponseBody = z.infer<typeof activateJobsResponseBodySchema>;

const activateJobs: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/activation`;
	},
};

const failJobRequestBodySchema = z.object({
	retries: z.number().optional(),
	errorMessage: z.string().optional(),
	retryBackOff: z.number().optional(),
	variables: z.record(z.string(), z.unknown()).nullable(),
});
type FailJobRequestBody = z.infer<typeof failJobRequestBodySchema>;

const failJob: Endpoint<Pick<Job, 'jobKey'>> = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/failure`;
	},
};

const throwJobErrorRequestBodySchema = z.object({
	errorCode: z.string(),
	errorMessage: z.string().optional(),
	variables: z.record(z.string(), z.unknown()).nullable(),
});
type ThrowJobErrorRequestBody = z.infer<typeof throwJobErrorRequestBodySchema>;

const throwJobError: Endpoint<Pick<Job, 'jobKey'>> = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/error`;
	},
};

const jobResultCorrectionsSchema = z.object({}).passthrough();
type JobResultCorrections = z.infer<typeof jobResultCorrectionsSchema>;

const jobResultSchema = z.object({
	denied: z.boolean().nullable(),
	deniedReason: z.string().nullable(),
	corrections: jobResultCorrectionsSchema.nullable(),
});
type JobResult = z.infer<typeof jobResultSchema>;

const completeJobRequestBodySchema = z.object({
	variables: z.record(z.string(), z.unknown()).nullable(),
	result: jobResultSchema.optional(),
});
type CompleteJobRequestBody = z.infer<typeof completeJobRequestBodySchema>;

const completeJob: Endpoint<Pick<Job, 'jobKey'>> = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/completion`;
	},
};

const jobChangesetSchema = z.object({
	retries: z.number().optional(),
	timeout: z.number().optional(),
});
type JobChangeset = z.infer<typeof jobChangesetSchema>;

const updateJobRequestBodySchema = z.object({
	changeset: jobChangesetSchema,
});
type UpdateJobRequestBody = z.infer<typeof updateJobRequestBodySchema>;

const updateJob: Endpoint<Pick<Job, 'jobKey'>> = {
	method: 'PATCH',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}`;
	},
};

export {
	queryJobs,
	activateJobs,
	failJob,
	throwJobError,
	completeJob,
	updateJob,
	queryJobsRequestBodySchema,
	queryJobsResponseBodySchema,
	activateJobsRequestBodySchema,
	activateJobsResponseBodySchema,
	failJobRequestBodySchema,
	throwJobErrorRequestBodySchema,
	completeJobRequestBodySchema,
	updateJobRequestBodySchema,
	jobSchema,
	activatedJobSchema,
	jobResultSchema,
	jobChangesetSchema,
	jobStateSchema,
	jobKindSchema,
	listenerEventTypeSchema,
	jobStateFilterSchema,
	jobKindFilterSchema,
	listenerEventTypeFilterSchema,
};
export type {
	QueryJobsRequestBody,
	QueryJobsResponseBody,
	ActivateJobsRequestBody,
	ActivateJobsResponseBody,
	FailJobRequestBody,
	ThrowJobErrorRequestBody,
	CompleteJobRequestBody,
	UpdateJobRequestBody,
	Job,
	ActivatedJob,
	JobResult,
	JobResultCorrections,
	JobChangeset,
	JobState,
	JobKind,
	ListenerEventType,
};
