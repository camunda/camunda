/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {activatedJobResultSchema} from './gen/zod/activatedJobResultSchema';
import {jobActivationRequestSchema} from './gen/zod/jobActivationRequestSchema';
import {jobActivationResultSchema} from './gen/zod/jobActivationResultSchema';
import {jobChangesetSchema as genJobChangesetSchema} from './gen/zod/jobChangesetSchema';
import {jobCompletionRequestSchema} from './gen/zod/jobCompletionRequestSchema';
import {jobErrorRequestSchema} from './gen/zod/jobErrorRequestSchema';
import {jobFailRequestSchema} from './gen/zod/jobFailRequestSchema';
import {jobKindEnumSchema} from './gen/zod/jobKindEnumSchema';
import {jobKindFilterPropertySchema} from './gen/zod/jobKindFilterPropertySchema';
import {jobListenerEventTypeEnumSchema} from './gen/zod/jobListenerEventTypeEnumSchema';
import {jobListenerEventTypeFilterPropertySchema} from './gen/zod/jobListenerEventTypeFilterPropertySchema';
import {jobResultSchema as genJobResultSchema} from './gen/zod/jobResultSchema';
import {jobSearchQueryResultSchema} from './gen/zod/jobSearchQueryResultSchema';
import {jobSearchQuerySchema} from './gen/zod/jobSearchQuerySchema';
import {jobSearchResultSchema} from './gen/zod/jobSearchResultSchema';
import {jobStateEnumSchema} from './gen/zod/jobStateEnumSchema';
import {jobStateFilterPropertySchema} from './gen/zod/jobStateFilterPropertySchema';
import {jobUpdateRequestSchema} from './gen/zod/jobUpdateRequestSchema';
import type {ActivatedJobResult} from './gen/types/ActivatedJobResult';
import type {JobActivationRequest} from './gen/types/JobActivationRequest';
import type {JobActivationResult} from './gen/types/JobActivationResult';
import type {JobChangeset as GenJobChangeset} from './gen/types/JobChangeset';
import type {JobCompletionRequest} from './gen/types/JobCompletionRequest';
import type {JobErrorRequest} from './gen/types/JobErrorRequest';
import type {JobFailRequest} from './gen/types/JobFailRequest';
import type {JobKindEnumKey} from './gen/types/JobKindEnum';
import type {JobListenerEventTypeEnumKey} from './gen/types/JobListenerEventTypeEnum';
import type {JobResult as GenJobResult} from './gen/types/JobResult';
import type {JobResultCorrections as GenJobResultCorrections} from './gen/types/JobResultCorrections';
import type {JobSearchQuery} from './gen/types/JobSearchQuery';
import type {JobSearchQueryResult} from './gen/types/JobSearchQueryResult';
import type {JobSearchResult} from './gen/types/JobSearchResult';
import type {JobStateEnumKey} from './gen/types/JobStateEnum';
import type {JobUpdateRequest} from './gen/types/JobUpdateRequest';

const jobStateSchema = jobStateEnumSchema;
type JobState = JobStateEnumKey;

const jobKindSchema = jobKindEnumSchema;
type JobKind = JobKindEnumKey;

const listenerEventTypeSchema = jobListenerEventTypeEnumSchema;
type ListenerEventType = JobListenerEventTypeEnumKey;

const jobStateFilterSchema = jobStateFilterPropertySchema;
const jobKindFilterSchema = jobKindFilterPropertySchema;
const listenerEventTypeFilterSchema = jobListenerEventTypeFilterPropertySchema;

const jobSchema = jobSearchResultSchema;
type Job = JobSearchResult;

const queryJobsRequestBodySchema = jobSearchQuerySchema;
type QueryJobsRequestBody = JobSearchQuery;

const queryJobsResponseBodySchema = jobSearchQueryResultSchema;
type QueryJobsResponseBody = JobSearchQueryResult;

const activateJobsRequestBodySchema = jobActivationRequestSchema;
type ActivateJobsRequestBody = JobActivationRequest;

const activatedJobSchema = activatedJobResultSchema;
type ActivatedJob = ActivatedJobResult;

const activateJobsResponseBodySchema = jobActivationResultSchema;
type ActivateJobsResponseBody = JobActivationResult;

const failJobRequestBodySchema = jobFailRequestSchema;
type FailJobRequestBody = JobFailRequest;

const throwJobErrorRequestBodySchema = jobErrorRequestSchema;
type ThrowJobErrorRequestBody = JobErrorRequest;

type JobResultCorrections = GenJobResultCorrections;

const jobResultSchema = genJobResultSchema;
type JobResult = GenJobResult;

const completeJobRequestBodySchema = jobCompletionRequestSchema;
type CompleteJobRequestBody = JobCompletionRequest;

const jobChangesetSchema = genJobChangesetSchema;
type JobChangeset = GenJobChangeset;

const updateJobRequestBodySchema = jobUpdateRequestSchema;
type UpdateJobRequestBody = JobUpdateRequest;

const queryJobs = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/search` as const;
	},
} as const satisfies Endpoint;

const activateJobs = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/jobs/activation` as const;
	},
} as const satisfies Endpoint;

const failJob = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/failure` as const;
	},
} as const satisfies Endpoint<Pick<Job, 'jobKey'>>;

const throwJobError = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/error` as const;
	},
} as const satisfies Endpoint<Pick<Job, 'jobKey'>>;

const completeJob = {
	method: 'POST',
	getUrl(params) {
		const {jobKey} = params;

		return `/${API_VERSION}/jobs/${jobKey}/completion` as const;
	},
} as const satisfies Endpoint<Pick<Job, 'jobKey'>>;

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
