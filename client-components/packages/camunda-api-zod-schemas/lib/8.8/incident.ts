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
	getQueryResponseBodySchema,
	getQueryRequestBodySchema,
	advancedDateTimeFilterSchema,
	getEnumFilterSchema,
	type Endpoint,
} from '../common';

const incidentErrorTypeSchema = z.enum([
	'UNSPECIFIED',
	'UNKNOWN',
	'IO_MAPPING_ERROR',
	'JOB_NO_RETRIES',
	'EXECUTION_LISTENER_NO_RETRIES',
	'TASK_LISTENER_NO_RETRIES',
	'CONDITION_ERROR',
	'EXTRACT_VALUE_ERROR',
	'CALLED_ELEMENT_ERROR',
	'UNHANDLED_ERROR_EVENT',
	'MESSAGE_SIZE_EXCEEDED',
	'CALLED_DECISION_ERROR',
	'DECISION_EVALUATION_ERROR',
	'FORM_NOT_FOUND',
	'RESOURCE_NOT_FOUND',
]);
type IncidentErrorType = z.infer<typeof incidentErrorTypeSchema>;

const incidentStateSchema = z.enum(['ACTIVE', 'MIGRATED', 'RESOLVED', 'PENDING']);
type IncidentState = z.infer<typeof incidentStateSchema>;

const incidentSchema = z.object({
	processDefinitionId: z.string(),
	errorType: incidentErrorTypeSchema,
	errorMessage: z.string(),
	elementId: z.string(),
	creationTime: z.string(),
	state: incidentStateSchema,
	tenantId: z.string(),
	incidentKey: z.string(),
	processDefinitionKey: z.string(),
	processInstanceKey: z.string(),
	elementInstanceKey: z.string(),
	jobKey: z.string().optional(),
});
type Incident = z.infer<typeof incidentSchema>;

const resolveIncident: Endpoint<Pick<Incident, 'incidentKey'>> = {
	method: 'POST',
	getUrl: ({incidentKey}) => `/${API_VERSION}/incidents/${incidentKey}/resolution`,
};

const getIncident: Endpoint<Pick<Incident, 'incidentKey'>> = {
	method: 'GET',
	getUrl: ({incidentKey}) => `/${API_VERSION}/incidents/${incidentKey}`,
};

const getIncidentResponseBodySchema = incidentSchema;
type GetIncidentResponseBody = z.infer<typeof getIncidentResponseBodySchema>;

const queryIncidentsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'incidentKey',
		'processDefinitionKey',
		'processDefinitionId',
		'processInstanceKey',
		'errorType',
		'errorMessage',
		'elementId',
		'elementInstanceKey',
		'creationTime',
		'state',
		'jobKey',
		'tenantId',
	] as const,
	filter: z
		.object({
			errorType: getEnumFilterSchema(incidentErrorTypeSchema),
			creationTime: advancedDateTimeFilterSchema,
			state: getEnumFilterSchema(incidentStateSchema),
			...incidentSchema.pick({
				processDefinitionId: true,
				errorMessage: true,
				elementId: true,
				tenantId: true,
				incidentKey: true,
				processDefinitionKey: true,
				processInstanceKey: true,
				elementInstanceKey: true,
				jobKey: true,
			}).shape,
		})
		.partial(),
});
type QueryIncidentsRequestBody = z.infer<typeof queryIncidentsRequestBodySchema>;

const queryIncidentsResponseBodySchema = getQueryResponseBodySchema(incidentSchema);
type QueryIncidentsResponseBody = z.infer<typeof queryIncidentsResponseBodySchema>;

const queryIncidents: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/search`,
};

export {
	resolveIncident,
	getIncident,
	queryIncidents,
	getIncidentResponseBodySchema,
	queryIncidentsRequestBodySchema,
	queryIncidentsResponseBodySchema,
	incidentErrorTypeSchema,
	incidentStateSchema,
	incidentSchema,
};
export type {
	IncidentErrorType,
	IncidentState,
	Incident,
	GetIncidentResponseBody,
	QueryIncidentsRequestBody,
	QueryIncidentsResponseBody,
};
