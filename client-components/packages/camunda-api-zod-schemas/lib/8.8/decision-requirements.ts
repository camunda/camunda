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

const queryDecisionRequirements: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-requirements/search`,
};

const getDecisionRequirements: Endpoint<{decisionRequirementsKey: string}> = {
	method: 'GET',
	getUrl: ({decisionRequirementsKey}) => `/${API_VERSION}/decision-requirements/${decisionRequirementsKey}`,
};

const getDecisionRequirementsXml: Endpoint<{decisionRequirementsKey: string}> = {
	method: 'GET',
	getUrl: ({decisionRequirementsKey}) => `/${API_VERSION}/decision-requirements/${decisionRequirementsKey}/xml`,
};

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
