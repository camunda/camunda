/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {getIncidentStatus200Schema} from './gen/zod/getIncidentSchema';
import {incidentErrorTypeEnumSchema} from './gen/zod/incidentErrorTypeEnumSchema';
import {incidentResultSchema} from './gen/zod/incidentResultSchema';
import {incidentSearchQueryResultSchema} from './gen/zod/incidentSearchQueryResultSchema';
import {incidentSearchQuerySchema} from './gen/zod/incidentSearchQuerySchema';
import {incidentStateEnumSchema} from './gen/zod/incidentStateEnumSchema';
import type {GetIncidentStatus200} from './gen/types/GetIncident';
import type {IncidentErrorTypeEnumKey} from './gen/types/IncidentErrorTypeEnum';
import type {IncidentResult} from './gen/types/IncidentResult';
import type {IncidentSearchQuery} from './gen/types/IncidentSearchQuery';
import type {IncidentSearchQueryResult} from './gen/types/IncidentSearchQueryResult';
import type {IncidentStateEnumKey} from './gen/types/IncidentStateEnum';

const incidentErrorTypeSchema = incidentErrorTypeEnumSchema;
type IncidentErrorType = IncidentErrorTypeEnumKey;

const incidentStateSchema = incidentStateEnumSchema;
type IncidentState = IncidentStateEnumKey;

const incidentSchema = incidentResultSchema;
type Incident = IncidentResult;

const getIncidentResponseBodySchema = getIncidentStatus200Schema;
type GetIncidentResponseBody = GetIncidentStatus200;

const queryIncidentsRequestBodySchema = incidentSearchQuerySchema;
type QueryIncidentsRequestBody = IncidentSearchQuery;

const queryIncidentsResponseBodySchema = incidentSearchQueryResultSchema;
type QueryIncidentsResponseBody = IncidentSearchQueryResult;

const resolveIncident = {
	method: 'POST',
	getUrl: ({incidentKey}) => `/${API_VERSION}/incidents/${incidentKey}/resolution` as const,
} as const satisfies Endpoint<Pick<Incident, 'incidentKey'>>;

const getIncident = {
	method: 'GET',
	getUrl: ({incidentKey}) => `/${API_VERSION}/incidents/${incidentKey}` as const,
} as const satisfies Endpoint<Pick<Incident, 'incidentKey'>>;

const queryIncidents = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/incidents/search` as const,
} as const satisfies Endpoint;

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
