/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {decisionRequirementsResultSchema} from './gen/zod/decisionRequirementsResultSchema';
import {decisionRequirementsSearchQueryResultSchema} from './gen/zod/decisionRequirementsSearchQueryResultSchema';
import {decisionRequirementsSearchQuerySchema} from './gen/zod/decisionRequirementsSearchQuerySchema';
import {getDecisionRequirementsXMLStatus200Schema} from './gen/zod/getDecisionRequirementsXMLSchema';
import type {DecisionRequirementsResult} from './gen/types/DecisionRequirementsResult';
import type {DecisionRequirementsSearchQuery} from './gen/types/DecisionRequirementsSearchQuery';
import type {DecisionRequirementsSearchQueryResult} from './gen/types/DecisionRequirementsSearchQueryResult';
import type {GetDecisionRequirementsXMLStatus200} from './gen/types/GetDecisionRequirementsXML';

const decisionRequirementsSchema = decisionRequirementsResultSchema;
type DecisionRequirements = DecisionRequirementsResult;

const queryDecisionRequirementsRequestBodySchema = decisionRequirementsSearchQuerySchema;
type QueryDecisionRequirementsRequestBody = DecisionRequirementsSearchQuery;

const queryDecisionRequirementsResponseBodySchema = decisionRequirementsSearchQueryResultSchema;
type QueryDecisionRequirementsResponseBody = DecisionRequirementsSearchQueryResult;

const getDecisionRequirementsXmlResponseBodySchema = getDecisionRequirementsXMLStatus200Schema;
type GetDecisionRequirementsXmlResponseBody = GetDecisionRequirementsXMLStatus200;

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
