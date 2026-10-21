/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Job, QueryJobsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {createPaginatedResponse} from './shared';

function createJob(overrides?: Partial<Job>): Job {
	return {
		customHeaders: {},
		deadline: null,
		deniedReason: null,
		elementId: 'service-task',
		elementInstanceKey: '2251799813685281',
		endTime: null,
		errorCode: null,
		errorMessage: null,
		hasFailedWithRetriesLeft: false,
		isDenied: null,
		jobKey: '2251799813685290',
		kind: 'BPMN_ELEMENT',
		listenerEventType: 'UNSPECIFIED',
		processDefinitionId: 'my-process',
		processDefinitionKey: '2251799813685279',
		processInstanceKey: '2251799813685280',
		rootProcessInstanceKey: null,
		businessId: null,
		retries: 3,
		state: 'CREATED',
		tenantId: '<default>',
		type: 'send-email',
		worker: 'email-worker',
		creationTime: null,
		lastUpdateTime: null,
		priority: 50,
		storageOrdinal: null,
		...overrides,
	};
}

function createQueryJobsResponse(items: Job[] = []): QueryJobsResponseBody {
	return createPaginatedResponse({
		items,
		page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	});
}

function createListenerJob(overrides?: Partial<Job>): Job {
	return createJob({
		type: 'io.camunda:execution-listener',
		state: 'COMPLETED',
		kind: 'EXECUTION_LISTENER',
		listenerEventType: 'START',
		elementId: 'user-task',
		worker: 'worker-1',
		priority: 0,
		isDenied: false,
		endTime: '2026-01-15T10:05:00.000Z',
		...overrides,
	});
}

export {createJob, createListenerJob, createQueryJobsResponse};
