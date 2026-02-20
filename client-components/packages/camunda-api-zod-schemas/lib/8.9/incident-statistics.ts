/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, type Endpoint} from '../common';
import {getQueryResponseBodySchema} from './common';

const getIncidentProcessInstanceStatisticsByError: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/statistics/process-instances-by-error`,
};

const getIncidentProcessInstanceStatisticsByDefinition: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/statistics/process-instances-by-definition`,
};

const incidentProcessInstanceStatisticsByErrorSchema = z.object({
	errorHashCode: z.number(),
	errorMessage: z.string(),
	activeInstancesWithErrorCount: z.number(),
});

const incidentProcessInstanceStatisticsByDefinitionSchema = z.object({
	processDefinitionId: z.string(),
	processDefinitionKey: z.string(),
	processDefinitionName: z.string().nullable(),
	processDefinitionVersion: z.number(),
	tenantId: z.string(),
	activeInstancesWithErrorCount: z.number(),
});

type IncidentProcessInstanceStatisticsByError = z.infer<typeof incidentProcessInstanceStatisticsByErrorSchema>;

type IncidentProcessInstanceStatisticsByDefinition = z.infer<
	typeof incidentProcessInstanceStatisticsByDefinitionSchema
>;

const getIncidentProcessInstanceStatisticsByErrorRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['errorMessage', 'activeInstancesWithErrorCount'] as const,
	filter: z.never(),
});

const incidentProcessInstanceStatisticsByDefinitionFilterSchema = z.object({
	errorHashCode: z.number(),
});

const getIncidentProcessInstanceStatisticsByDefinitionRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['processDefinitionKey', 'activeInstancesWithErrorCount', 'tenantId'] as const,
	filter: incidentProcessInstanceStatisticsByDefinitionFilterSchema,
});

type GetIncidentProcessInstanceStatisticsByErrorRequestBody = z.infer<
	typeof getIncidentProcessInstanceStatisticsByErrorRequestBodySchema
>;

type GetIncidentProcessInstanceStatisticsByDefinitionRequestBody = z.infer<
	typeof getIncidentProcessInstanceStatisticsByDefinitionRequestBodySchema
>;

const getIncidentProcessInstanceStatisticsByErrorResponseBodySchema = getQueryResponseBodySchema(
	incidentProcessInstanceStatisticsByErrorSchema,
);

const getIncidentProcessInstanceStatisticsByDefinitionResponseBodySchema = getQueryResponseBodySchema(
	incidentProcessInstanceStatisticsByDefinitionSchema,
);

type GetIncidentProcessInstanceStatisticsByErrorResponseBody = z.infer<
	typeof getIncidentProcessInstanceStatisticsByErrorResponseBodySchema
>;

type GetIncidentProcessInstanceStatisticsByDefinitionResponseBody = z.infer<
	typeof getIncidentProcessInstanceStatisticsByDefinitionResponseBodySchema
>;

export {
	getIncidentProcessInstanceStatisticsByError,
	incidentProcessInstanceStatisticsByErrorSchema,
	getIncidentProcessInstanceStatisticsByErrorRequestBodySchema,
	getIncidentProcessInstanceStatisticsByErrorResponseBodySchema,
	getIncidentProcessInstanceStatisticsByDefinition,
	incidentProcessInstanceStatisticsByDefinitionSchema,
	getIncidentProcessInstanceStatisticsByDefinitionRequestBodySchema,
	getIncidentProcessInstanceStatisticsByDefinitionResponseBodySchema,
};
export type {
	IncidentProcessInstanceStatisticsByError,
	GetIncidentProcessInstanceStatisticsByErrorRequestBody,
	GetIncidentProcessInstanceStatisticsByErrorResponseBody,
	IncidentProcessInstanceStatisticsByDefinition,
	GetIncidentProcessInstanceStatisticsByDefinitionRequestBody,
	GetIncidentProcessInstanceStatisticsByDefinitionResponseBody,
};
