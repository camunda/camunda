/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';
import {
	incidentProcessInstanceStatisticsByErrorResultSchema,
	incidentProcessInstanceStatisticsByDefinitionResultSchema,
	incidentProcessInstanceStatisticsByDefinitionFilterSchema as genIncidentProcessInstanceStatisticsByDefinitionFilterSchema,
	incidentProcessInstanceStatisticsByErrorQuerySchema,
	incidentProcessInstanceStatisticsByErrorQueryResultSchema,
	incidentProcessInstanceStatisticsByDefinitionQuerySchema,
	incidentProcessInstanceStatisticsByDefinitionQueryResultSchema,
} from './gen';

const getIncidentProcessInstanceStatisticsByError: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/statistics/process-instances-by-error`,
};

const getIncidentProcessInstanceStatisticsByDefinition: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/statistics/process-instances-by-definition`,
};

const incidentProcessInstanceStatisticsByErrorSchema = incidentProcessInstanceStatisticsByErrorResultSchema;

const incidentProcessInstanceStatisticsByDefinitionSchema = incidentProcessInstanceStatisticsByDefinitionResultSchema;

type IncidentProcessInstanceStatisticsByError = z.infer<typeof incidentProcessInstanceStatisticsByErrorSchema>;

type IncidentProcessInstanceStatisticsByDefinition = z.infer<
	typeof incidentProcessInstanceStatisticsByDefinitionSchema
>;

const getIncidentProcessInstanceStatisticsByErrorRequestBodySchema =
	incidentProcessInstanceStatisticsByErrorQuerySchema;

const incidentProcessInstanceStatisticsByDefinitionFilterSchema =
	genIncidentProcessInstanceStatisticsByDefinitionFilterSchema;

const getIncidentProcessInstanceStatisticsByDefinitionRequestBodySchema =
	incidentProcessInstanceStatisticsByDefinitionQuerySchema;

type GetIncidentProcessInstanceStatisticsByErrorRequestBody = z.infer<
	typeof getIncidentProcessInstanceStatisticsByErrorRequestBodySchema
>;

type GetIncidentProcessInstanceStatisticsByDefinitionRequestBody = z.infer<
	typeof getIncidentProcessInstanceStatisticsByDefinitionRequestBodySchema
>;

const getIncidentProcessInstanceStatisticsByErrorResponseBodySchema =
	incidentProcessInstanceStatisticsByErrorQueryResultSchema;

const getIncidentProcessInstanceStatisticsByDefinitionResponseBodySchema =
	incidentProcessInstanceStatisticsByDefinitionQueryResultSchema;

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
