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

const queryJobs = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/search` as const;
	},
} as const satisfies Endpoint;

const queryJobsResponseBodySchema = jobSearchQueryResultSchema;
type QueryJobsResponseBody = z.infer<typeof queryJobsResponseBodySchema>;

const activateJobsRequestBodySchema = jobActivationRequestSchema;
type ActivateJobsRequestBody = z.infer<typeof activateJobsRequestBodySchema>;

const activatedJobSchema = activatedJobResultSchema;
type ActivatedJob = z.infer<typeof activatedJobSchema>;

const activateJobsResponseBodySchema = jobActivationResultSchema;
type ActivateJobsResponseBody = z.infer<typeof activateJobsResponseBodySchema>;

const activateJobs = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/activation` as const;
	},
} as const satisfies Endpoint;

const failJobRequestBodySchema = jobFailRequestSchema;
type FailJobRequestBody = z.infer<typeof failJobRequestBodySchema>;

const failJob = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/failure` as const;
	},
} as const satisfies Endpoint<Pick<Job, 'jobKey'>>;

const throwJobErrorRequestBodySchema = jobErrorRequestSchema;
type ThrowJobErrorRequestBody = z.infer<typeof throwJobErrorRequestBodySchema>;

const throwJobError = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/error` as const;
	},
} as const satisfies Endpoint<Pick<Job, 'jobKey'>>;

const jobResultCorrections = jobResultCorrectionsSchema;
type JobResultCorrections = z.infer<typeof jobResultCorrections>;

type JobResult = z.infer<typeof jobResultSchema>;

const completeJobRequestBodySchema = jobCompletionRequestSchema;
type CompleteJobRequestBody = z.infer<typeof completeJobRequestBodySchema>;

const completeJob = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/completion` as const;
	},
} as const satisfies Endpoint<Pick<Job, 'jobKey'>>;

type JobChangeset = z.infer<typeof jobChangesetSchema>;

const updateJobRequestBodySchema = jobUpdateRequestSchema;
type UpdateJobRequestBody = z.infer<typeof updateJobRequestBodySchema>;

const updateJob = {
	method: 'PATCH',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}` as const;
	},
} as const satisfies Endpoint<Pick<Job, 'jobKey'>>;

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
