/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getCollectionResponseBodySchema, type Endpoint} from './common';

const activityTypeSchema = z.enum([
	'UNSPECIFIED',
	'PROCESS',
	'SUB_PROCESS',
	'EVENT_SUB_PROCESS',
	'INTERMEDIATE_CATCH_EVENT',
	'INTERMEDIATE_THROW_EVENT',
	'BOUNDARY_EVENT',
	'SERVICE_TASK',
	'RECEIVE_TASK',
	'USER_TASK',
	'MANUAL_TASK',
	'TASK',
	'MULTI_INSTANCE_BODY',
	'CALL_ACTIVITY',
	'BUSINESS_RULE_TASK',
	'SCRIPT_TASK',
	'SEND_TASK',
	'UNKNOWN',
]);
type ActivityType = z.infer<typeof activityTypeSchema>;

const adHocSubProcessActivityFilterSchema = z.object({
	processDefinitionKey: z.string(),
	adHocSubProcessId: z.string(),
});

const queryActivatableActivitiesRequestBodySchema = z.object({
	filter: adHocSubProcessActivityFilterSchema,
});
type QueryActivatableActivitiesRequestBody = z.infer<typeof queryActivatableActivitiesRequestBodySchema>;

const activatableActivitySchema = z.object({
	processDefinitionKey: z.string(),
	processDefinitionId: z.string(),
	adHocSubProcessId: z.string(),
	elementId: z.string(),
	elementName: z.string(),
	type: activityTypeSchema,
	documentation: z.string(),
	tenantId: z.string(),
});
type ActivatableActivity = z.infer<typeof activatableActivitySchema>;

const queryActivatableActivitiesResponseBodySchema = getCollectionResponseBodySchema(activatableActivitySchema);
type QueryActivatableActivitiesResponseBody = z.infer<typeof queryActivatableActivitiesResponseBodySchema>;

const activateActivityWithinAdHocSubProcessRequestBodySchema = z.object({
	elementId: z.string(),
});
type ActivateActivityWithinAdHocSubProcessRequestBody = z.infer<
	typeof activateActivityWithinAdHocSubProcessRequestBodySchema
>;

const activateActivityWithinAdHocSubProcessResponseBodySchema = z.void();
type ActivateActivityWithinAdHocSubProcessResponseBody = z.infer<
	typeof activateActivityWithinAdHocSubProcessResponseBodySchema
>;

const activateAdHocSubProcessActivities: Endpoint<{
	adHocSubProcessInstanceKey: string;
}> = {
	method: 'POST',
	getUrl: ({adHocSubProcessInstanceKey}) =>
		`/${API_VERSION}/element-instances/ad-hoc-activities/${adHocSubProcessInstanceKey}/activation`,
};

export {
	activityTypeSchema,
	queryActivatableActivitiesRequestBodySchema,
	queryActivatableActivitiesResponseBodySchema,
	activateActivityWithinAdHocSubProcessRequestBodySchema,
	activateActivityWithinAdHocSubProcessResponseBodySchema,
	activateAdHocSubProcessActivities,
};

export type {
	ActivityType,
	QueryActivatableActivitiesRequestBody,
	ActivatableActivity,
	QueryActivatableActivitiesResponseBody,
	ActivateActivityWithinAdHocSubProcessRequestBody,
	ActivateActivityWithinAdHocSubProcessResponseBody,
};
