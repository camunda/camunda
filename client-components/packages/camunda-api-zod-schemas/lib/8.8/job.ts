/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	jobStateEnumSchema,
	jobKindEnumSchema,
	jobListenerEventTypeEnumSchema,
	jobSearchResultSchema,
	jobSearchQuerySchema,
	jobSearchQueryResultSchema,
	jobActivationRequestSchema,
	jobActivationResultSchema,
	activatedJobResultSchema,
	jobFailRequestSchema,
	jobErrorRequestSchema,
	jobCompletionRequestSchema,
	jobUpdateRequestSchema,
	jobChangesetSchema,
	jobResultSchema,
	jobResultCorrectionsSchema,
	jobStateFilterPropertySchema,
	jobKindFilterPropertySchema,
	jobListenerEventTypeFilterPropertySchema,
} from './gen';

const jobStateSchema = jobStateEnumSchema;
type JobState = z.infer<typeof jobStateSchema>;

const jobKindSchema = jobKindEnumSchema;
type JobKind = z.infer<typeof jobKindSchema>;

const listenerEventTypeSchema = jobListenerEventTypeEnumSchema;
type ListenerEventType = z.infer<typeof listenerEventTypeSchema>;

const jobStateFilterSchema = jobStateFilterPropertySchema;
const jobKindFilterSchema = jobKindFilterPropertySchema;
const listenerEventTypeFilterSchema = jobListenerEventTypeFilterPropertySchema;

const jobSchema = jobSearchResultSchema;
type Job = z.infer<typeof jobSchema>;

const queryJobsRequestBodySchema = jobSearchQuerySchema;
type QueryJobsRequestBody = z.infer<typeof queryJobsRequestBodySchema>;

const queryJobs: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/search`;
	},
};

const queryJobsResponseBodySchema = jobSearchQueryResultSchema;
type QueryJobsResponseBody = z.infer<typeof queryJobsResponseBodySchema>;

const activateJobsRequestBodySchema = jobActivationRequestSchema;
type ActivateJobsRequestBody = z.infer<typeof activateJobsRequestBodySchema>;

const activatedJobSchema = activatedJobResultSchema;
type ActivatedJob = z.infer<typeof activatedJobSchema>;

const activateJobsResponseBodySchema = jobActivationResultSchema;
type ActivateJobsResponseBody = z.infer<typeof activateJobsResponseBodySchema>;

const activateJobs: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/activation`;
	},
};

const failJobRequestBodySchema = jobFailRequestSchema;
type FailJobRequestBody = z.infer<typeof failJobRequestBodySchema>;

const failJob: Endpoint<{jobKey: string}> = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/failure`;
	},
};

const throwJobErrorRequestBodySchema = jobErrorRequestSchema;
type ThrowJobErrorRequestBody = z.infer<typeof throwJobErrorRequestBodySchema>;

const throwJobError: Endpoint<{jobKey: string}> = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/error`;
	},
};

const jobResultCorrections = jobResultCorrectionsSchema;
type JobResultCorrections = z.infer<typeof jobResultCorrections>;

type JobResult = z.infer<typeof jobResultSchema>;

const completeJobRequestBodySchema = jobCompletionRequestSchema;
type CompleteJobRequestBody = z.infer<typeof completeJobRequestBodySchema>;

const completeJob: Endpoint<{jobKey: string}> = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/completion`;
	},
};

type JobChangeset = z.infer<typeof jobChangesetSchema>;

const updateJobRequestBodySchema = jobUpdateRequestSchema;
type UpdateJobRequestBody = z.infer<typeof updateJobRequestBodySchema>;

const updateJob: Endpoint<{jobKey: string}> = {
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
