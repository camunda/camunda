/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, getQueryResponseBodySchema, type Endpoint} from '../common';

const decisionRequirementsSchema = z.object({
	decisionRequirementsName: z.string(),
	version: z.number(),
	decisionRequirementsId: z.string(),
	resourceName: z.string(),
	tenantId: z.string(),
	decisionRequirementsKey: z.string(),
});
type DecisionRequirements = z.infer<typeof decisionRequirementsSchema>;

const queryDecisionRequirementsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'decisionRequirementsKey',
		'decisionRequirementsName',
		'version',
		'decisionRequirementsId',
		'tenantId',
	] as const,
	filter: decisionRequirementsSchema.partial(),
});
type QueryDecisionRequirementsRequestBody = z.infer<typeof queryDecisionRequirementsRequestBodySchema>;

const queryDecisionRequirementsResponseBodySchema = getQueryResponseBodySchema(decisionRequirementsSchema);
type QueryDecisionRequirementsResponseBody = z.infer<typeof queryDecisionRequirementsResponseBodySchema>;

const getDecisionRequirementsXmlResponseBodySchema = z.string();
type GetDecisionRequirementsXmlResponseBody = z.infer<typeof getDecisionRequirementsXmlResponseBodySchema>;

const queryDecisionRequirements: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-requirements/search`,
};

const getDecisionRequirements: Endpoint<Pick<DecisionRequirements, 'decisionRequirementsKey'>> = {
	method: 'GET',
	getUrl: ({decisionRequirementsKey}) => `/${API_VERSION}/decision-requirements/${decisionRequirementsKey}`,
};

const getDecisionRequirementsXml: Endpoint<Pick<DecisionRequirements, 'decisionRequirementsKey'>> = {
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
