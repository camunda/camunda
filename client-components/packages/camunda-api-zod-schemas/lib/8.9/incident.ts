/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {incidentResultSchema, incidentSearchQuerySchema, incidentSearchQueryResultSchema} from './gen';

const incidentErrorTypeSchema = incidentResultSchema.shape.errorType.unwrap();
type IncidentErrorType = z.infer<typeof incidentErrorTypeSchema>;

const incidentStateSchema = incidentResultSchema.shape.state.unwrap();
type IncidentState = z.infer<typeof incidentStateSchema>;

const incidentSchema = incidentResultSchema;
type Incident = z.infer<typeof incidentSchema>;

const resolveIncident: Endpoint<{incidentKey: string}> = {
	method: 'POST',
	getUrl: ({incidentKey}) => `/${API_VERSION}/incidents/${incidentKey}/resolution`,
};

const getIncident: Endpoint<{incidentKey: string}> = {
	method: 'GET',
	getUrl: ({incidentKey}) => `/${API_VERSION}/incidents/${incidentKey}`,
};

const getIncidentResponseBodySchema = incidentResultSchema;
type GetIncidentResponseBody = z.infer<typeof getIncidentResponseBodySchema>;

const queryIncidentsRequestBodySchema = incidentSearchQuerySchema;
type QueryIncidentsRequestBody = z.infer<typeof queryIncidentsRequestBodySchema>;

const queryIncidentsResponseBodySchema = incidentSearchQueryResultSchema;
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
