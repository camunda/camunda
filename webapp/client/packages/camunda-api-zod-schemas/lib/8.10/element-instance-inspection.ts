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
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from './common';
import {elementInstanceTypeSchema} from './element-instance';
import {jobKindSchema, listenerEventTypeSchema} from './job';

const waitStateTypeSchema = z.enum(['JOB', 'MESSAGE', 'USER_TASK', 'TIMER', 'SIGNAL']);
type WaitStateType = z.infer<typeof waitStateTypeSchema>;

const waitStateElementTypeSchema = elementInstanceTypeSchema;
type WaitStateElementType = z.infer<typeof waitStateElementTypeSchema>;

const jobWaitStateDetailsSchema = z.object({
	waitStateType: z.literal('JOB'),
	jobKey: z.string(),
	jobType: z.string(),
	jobKind: jobKindSchema,
	listenerEventType: listenerEventTypeSchema.nullable(),
	retries: z.number().int().nullable(),
});
type JobWaitStateDetails = z.infer<typeof jobWaitStateDetailsSchema>;

const messageWaitStateDetailsSchema = z.object({
	waitStateType: z.literal('MESSAGE'),
	messageName: z.string(),
	correlationKey: z.string().nullable(),
});
type MessageWaitStateDetails = z.infer<typeof messageWaitStateDetailsSchema>;

const userTaskWaitStateDetailsSchema = z.object({
	waitStateType: z.literal('USER_TASK'),
	taskKey: z.string(),
	dueDate: z.string().nullable(),
});
type UserTaskWaitStateDetails = z.infer<typeof userTaskWaitStateDetailsSchema>;

const timerWaitStateDetailsSchema = z.object({
	waitStateType: z.literal('TIMER'),
	dueDate: z.number().int().nullable(),
	repetitions: z.number().int().nullable(),
});
type TimerWaitStateDetails = z.infer<typeof timerWaitStateDetailsSchema>;

const signalWaitStateDetailsSchema = z.object({
	waitStateType: z.literal('SIGNAL'),
	signalName: z.string(),
});
type SignalWaitStateDetails = z.infer<typeof signalWaitStateDetailsSchema>;

const waitStateDetailsSchema = z.discriminatedUnion('waitStateType', [
	jobWaitStateDetailsSchema,
	messageWaitStateDetailsSchema,
	userTaskWaitStateDetailsSchema,
	timerWaitStateDetailsSchema,
	signalWaitStateDetailsSchema,
]);
type WaitStateDetails = z.infer<typeof waitStateDetailsSchema>;

const elementInstanceInspectionSchema = z.object({
	rootProcessInstanceKey: z.string().nullable(),
	processInstanceKey: z.string(),
	elementInstanceKey: z.string(),
	elementId: z.string(),
	elementType: waitStateElementTypeSchema,
	tenantId: z.string(),
	bpmnProcessId: z.string(),
	details: waitStateDetailsSchema,
});
type ElementInstanceInspection = z.infer<typeof elementInstanceInspectionSchema>;

const queryElementInstanceInspectionFilterSchema = z
	.object({
		processInstanceKey: advancedStringFilterSchema,
		elementInstanceKey: advancedStringFilterSchema,
		rootProcessInstanceKey: advancedStringFilterSchema,
		elementId: advancedStringFilterSchema,
		elementType: z.union([waitStateElementTypeSchema, getEnumFilterSchema(waitStateElementTypeSchema)]),
		waitStateType: z.union([waitStateTypeSchema, getEnumFilterSchema(waitStateTypeSchema)]),
	})
	.partial();

const queryElementInstanceInspectionRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['elementInstanceKey', 'processInstanceKey', 'rootProcessInstanceKey', 'elementId'] as const,
	filter: queryElementInstanceInspectionFilterSchema,
});
type QueryElementInstanceInspectionRequestBody = z.infer<typeof queryElementInstanceInspectionRequestBodySchema>;

const queryElementInstanceInspectionResponseBodySchema = getQueryResponseBodySchema(elementInstanceInspectionSchema);
type QueryElementInstanceInspectionResponseBody = z.infer<typeof queryElementInstanceInspectionResponseBodySchema>;

const queryElementInstanceInspection: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/element-instances/wait-states/search`;
	},
};

export {
	waitStateTypeSchema,
	waitStateElementTypeSchema,
	jobWaitStateDetailsSchema,
	messageWaitStateDetailsSchema,
	userTaskWaitStateDetailsSchema,
	timerWaitStateDetailsSchema,
	signalWaitStateDetailsSchema,
	waitStateDetailsSchema,
	elementInstanceInspectionSchema,
	queryElementInstanceInspectionRequestBodySchema,
	queryElementInstanceInspectionResponseBodySchema,
	queryElementInstanceInspection,
};

export type {
	WaitStateType,
	WaitStateElementType,
	JobWaitStateDetails,
	MessageWaitStateDetails,
	UserTaskWaitStateDetails,
	TimerWaitStateDetails,
	SignalWaitStateDetails,
	WaitStateDetails,
	ElementInstanceInspection,
	QueryElementInstanceInspectionRequestBody,
	QueryElementInstanceInspectionResponseBody,
};
