/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from '../common';
import {incidentProcessInstanceStatisticsByDefinitionQueryResultSchema} from './gen/zod/incidentProcessInstanceStatisticsByDefinitionQueryResultSchema';
import {incidentProcessInstanceStatisticsByDefinitionQuerySchema} from './gen/zod/incidentProcessInstanceStatisticsByDefinitionQuerySchema';
import {incidentProcessInstanceStatisticsByDefinitionResultSchema} from './gen/zod/incidentProcessInstanceStatisticsByDefinitionResultSchema';
import {incidentProcessInstanceStatisticsByErrorQueryResultSchema} from './gen/zod/incidentProcessInstanceStatisticsByErrorQueryResultSchema';
import {incidentProcessInstanceStatisticsByErrorQuerySchema} from './gen/zod/incidentProcessInstanceStatisticsByErrorQuerySchema';
import {incidentProcessInstanceStatisticsByErrorResultSchema} from './gen/zod/incidentProcessInstanceStatisticsByErrorResultSchema';
import type {IncidentProcessInstanceStatisticsByDefinitionQuery} from './gen/types/IncidentProcessInstanceStatisticsByDefinitionQuery';
import type {IncidentProcessInstanceStatisticsByDefinitionQueryResult} from './gen/types/IncidentProcessInstanceStatisticsByDefinitionQueryResult';
import type {IncidentProcessInstanceStatisticsByDefinitionResult} from './gen/types/IncidentProcessInstanceStatisticsByDefinitionResult';
import type {IncidentProcessInstanceStatisticsByErrorQuery} from './gen/types/IncidentProcessInstanceStatisticsByErrorQuery';
import type {IncidentProcessInstanceStatisticsByErrorQueryResult} from './gen/types/IncidentProcessInstanceStatisticsByErrorQueryResult';
import type {IncidentProcessInstanceStatisticsByErrorResult} from './gen/types/IncidentProcessInstanceStatisticsByErrorResult';

const incidentProcessInstanceStatisticsByErrorSchema = incidentProcessInstanceStatisticsByErrorResultSchema;
type IncidentProcessInstanceStatisticsByError = IncidentProcessInstanceStatisticsByErrorResult;

const incidentProcessInstanceStatisticsByDefinitionSchema = incidentProcessInstanceStatisticsByDefinitionResultSchema;
type IncidentProcessInstanceStatisticsByDefinition = IncidentProcessInstanceStatisticsByDefinitionResult;

const getIncidentProcessInstanceStatisticsByErrorRequestBodySchema =
	incidentProcessInstanceStatisticsByErrorQuerySchema;
type GetIncidentProcessInstanceStatisticsByErrorRequestBody = IncidentProcessInstanceStatisticsByErrorQuery;

const getIncidentProcessInstanceStatisticsByDefinitionRequestBodySchema =
	incidentProcessInstanceStatisticsByDefinitionQuerySchema;
type GetIncidentProcessInstanceStatisticsByDefinitionRequestBody = IncidentProcessInstanceStatisticsByDefinitionQuery;

const getIncidentProcessInstanceStatisticsByErrorResponseBodySchema =
	incidentProcessInstanceStatisticsByErrorQueryResultSchema;
type GetIncidentProcessInstanceStatisticsByErrorResponseBody = IncidentProcessInstanceStatisticsByErrorQueryResult;

const getIncidentProcessInstanceStatisticsByDefinitionResponseBodySchema =
	incidentProcessInstanceStatisticsByDefinitionQueryResultSchema;
type GetIncidentProcessInstanceStatisticsByDefinitionResponseBody =
	IncidentProcessInstanceStatisticsByDefinitionQueryResult;

const getIncidentProcessInstanceStatisticsByError = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/statistics/process-instances-by-error` as const,
} as const satisfies Endpoint;

const getIncidentProcessInstanceStatisticsByDefinition = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/statistics/process-instances-by-definition` as const,
} as const satisfies Endpoint;

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
