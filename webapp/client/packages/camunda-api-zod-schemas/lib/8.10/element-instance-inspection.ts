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

const waitStateTypeSchema = z.enum(['JOB', 'MESSAGE', 'TIMER', 'SIGNAL', 'CONDITION', 'CHILD_INSTANCE']);
type WaitStateType = z.infer<typeof waitStateTypeSchema>;

const waitStateDetailsSchema = z.record(z.string(), z.unknown());
type WaitStateDetails = z.infer<typeof waitStateDetailsSchema>;

const elementInstanceInspectionSchema = z.object({
	rootProcessInstanceKey: z.string(),
	processInstanceKey: z.string(),
	elementInstanceKey: z.string(),
	elementId: z.string(),
	elementType: elementInstanceTypeSchema,
	waitStateType: waitStateTypeSchema,
	details: waitStateDetailsSchema,
});
type ElementInstanceInspection = z.infer<typeof elementInstanceInspectionSchema>;

const queryElementInstanceInspectionFilterSchema = z
	.object({
		processInstanceKey: advancedStringFilterSchema,
		elementInstanceKey: advancedStringFilterSchema,
		elementId: advancedStringFilterSchema,
		waitStateType: z.union([waitStateTypeSchema, getEnumFilterSchema(waitStateTypeSchema)]),
	})
	.partial();

const queryElementInstanceInspectionRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['elementInstanceKey', 'processInstanceKey', 'elementId', 'waitStateType'] as const,
	filter: queryElementInstanceInspectionFilterSchema,
});
type QueryElementInstanceInspectionRequestBody = z.infer<typeof queryElementInstanceInspectionRequestBodySchema>;

const queryElementInstanceInspectionResponseBodySchema = getQueryResponseBodySchema(elementInstanceInspectionSchema);
type QueryElementInstanceInspectionResponseBody = z.infer<typeof queryElementInstanceInspectionResponseBodySchema>;

const queryElementInstanceInspection: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/element-instances/inspection`;
	},
};

export {
	waitStateTypeSchema,
	waitStateDetailsSchema,
	elementInstanceInspectionSchema,
	queryElementInstanceInspectionRequestBodySchema,
	queryElementInstanceInspectionResponseBodySchema,
	queryElementInstanceInspection,
};

export type {
	WaitStateType,
	WaitStateDetails,
	ElementInstanceInspection,
	QueryElementInstanceInspectionRequestBody,
	QueryElementInstanceInspectionResponseBody,
};
