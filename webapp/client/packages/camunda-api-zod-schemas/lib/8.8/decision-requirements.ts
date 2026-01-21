/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	decisionRequirementsResultSchema,
	decisionRequirementsSearchQuerySchema,
	decisionRequirementsSearchQueryResultSchema,
	getDecisionRequirementsXML200Schema,
} from './gen';

const decisionRequirementsSchema = decisionRequirementsResultSchema;
type DecisionRequirements = z.infer<typeof decisionRequirementsSchema>;

const queryDecisionRequirementsRequestBodySchema = decisionRequirementsSearchQuerySchema;
type QueryDecisionRequirementsRequestBody = z.infer<typeof queryDecisionRequirementsRequestBodySchema>;

const queryDecisionRequirementsResponseBodySchema = decisionRequirementsSearchQueryResultSchema;
type QueryDecisionRequirementsResponseBody = z.infer<typeof queryDecisionRequirementsResponseBodySchema>;

const getDecisionRequirementsXmlResponseBodySchema = getDecisionRequirementsXML200Schema;
type GetDecisionRequirementsXmlResponseBody = z.infer<typeof getDecisionRequirementsXmlResponseBodySchema>;

const queryDecisionRequirements = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-requirements/search` as const,
} as const satisfies Endpoint;

const getDecisionRequirements = {
	method: 'GET',
	getUrl: ({decisionRequirementsKey}) => `/${API_VERSION}/decision-requirements/${decisionRequirementsKey}` as const,
} as const satisfies Endpoint<Pick<DecisionRequirements, 'decisionRequirementsKey'>>;

const getDecisionRequirementsXml = {
	method: 'GET',
	getUrl: ({decisionRequirementsKey}) =>
		`/${API_VERSION}/decision-requirements/${decisionRequirementsKey}/xml` as const,
} as const satisfies Endpoint<Pick<DecisionRequirements, 'decisionRequirementsKey'>>;

export {
	decisionRequirementsSchema,
	queryDecisionRequirementsRequestBodySchema,
	queryDecisionRequirementsResponseBodySchema,
	getDecisionRequirementsXmlResponseBodySchema,
	queryDecisionRequirements,
	getDecisionRequirements,
	getDecisionRequirementsXml,
};
export type {
	DecisionRequirements,
	QueryDecisionRequirementsRequestBody,
	QueryDecisionRequirementsResponseBody,
	GetDecisionRequirementsXmlResponseBody,
};
