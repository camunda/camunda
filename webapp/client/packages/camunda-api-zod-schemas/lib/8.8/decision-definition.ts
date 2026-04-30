/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, getQueryResponseBodySchema, type Endpoint} from '../common';

const decisionDefinitionSchema = z.object({
	decisionDefinitionId: z.string(),
	name: z.string(),
	version: z.number(),
	decisionRequirementsId: z.string(),
	tenantId: z.string(),
	decisionDefinitionKey: z.string(),
	decisionRequirementsKey: z.string(),
});
type DecisionDefinition = z.infer<typeof decisionDefinitionSchema>;

const queryDecisionDefinitionsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'decisionDefinitionKey',
		'decisionDefinitionId',
		'name',
		'version',
		'decisionRequirementsId',
		'decisionRequirementsKey',
		'tenantId',
	] as const,
	filter: decisionDefinitionSchema
		.extend({
			isLatestVersion: z.boolean(),
		})
		.partial(),
});
type QueryDecisionDefinitionsRequestBody = z.infer<typeof queryDecisionDefinitionsRequestBodySchema>;

const queryDecisionDefinitionsResponseBodySchema = getQueryResponseBodySchema(decisionDefinitionSchema);
type QueryDecisionDefinitionsResponseBody = z.infer<typeof queryDecisionDefinitionsResponseBodySchema>;

const getDecisionDefinitionXmlResponseBodySchema = z.string();
type GetDecisionDefinitionXmlResponseBody = z.infer<typeof getDecisionDefinitionXmlResponseBodySchema>;

const evaluatedDecisionInputItemSchema = z.object({
	inputId: z.string(),
	inputName: z.string(),
	inputValue: z.string(),
});
type EvaluatedDecisionInputItem = z.infer<typeof evaluatedDecisionInputItemSchema>;

const evaluatedDecisionOutputItemSchema = z.object({
	outputId: z.string(),
	outputName: z.string(),
	outputValue: z.string(),
});
type EvaluatedDecisionOutputItem = z.infer<typeof evaluatedDecisionOutputItemSchema>;

const matchedDecisionRuleItemSchema = z.object({
	ruleId: z.string(),
	ruleIndex: z.number().int(),
	evaluatedOutputs: z.array(evaluatedDecisionOutputItemSchema),
});
type MatchedDecisionRuleItem = z.infer<typeof matchedDecisionRuleItemSchema>;

const evaluatedDecisionResultSchema = z.object({
	decisionDefinitionId: z.string(),
	decisionDefinitionName: z.string(),
	decisionDefinitionVersion: z.number().int(),
	decisionDefinitionType: z.string(),
	output: z.string(),
	tenantId: z.string(),
	matchedRules: z.array(matchedDecisionRuleItemSchema),
	evaluatedInputs: z.array(evaluatedDecisionInputItemSchema),
	decisionDefinitionKey: z.string(),
});
type EvaluatedDecisionResult = z.infer<typeof evaluatedDecisionResultSchema>;

const evaluateDecisionRequestBodySchema = z.object({
	decisionDefinitionId: z.string().optional(),
	variables: z.record(z.string(), z.unknown()).optional(),
	tenantId: z.string().optional(),
	decisionDefinitionKey: z.string().optional(),
});
type EvaluateDecisionRequestBody = z.infer<typeof evaluateDecisionRequestBodySchema>;

const evaluateDecisionResponseBodySchema = z.object({
	decisionDefinitionId: z.string(),
	decisionDefinitionName: z.string(),
	decisionDefinitionVersion: z.number().int(),
	decisionRequirementsId: z.string(),
	output: z.string(),
	failedDecisionDefinitionId: z.string().optional(),
	failureMessage: z.string().optional(),
	tenantId: z.string(),
	decisionDefinitionKey: z.string(),
	decisionRequirementsKey: z.string(),
	decisionInstanceKey: z.string(),
	evaluatedDecisions: z.array(evaluatedDecisionResultSchema),
});
type EvaluateDecisionResponseBody = z.infer<typeof evaluateDecisionResponseBodySchema>;

const queryDecisionDefinitions: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-definitions/search`,
};

const getDecisionDefinition: Endpoint<{decisionDefinitionKey: string}> = {
	method: 'GET',
	getUrl: ({decisionDefinitionKey}) => `/${API_VERSION}/decision-definitions/${decisionDefinitionKey}`,
};

const getDecisionDefinitionXml: Endpoint<{decisionDefinitionKey: string}> = {
	method: 'GET',
	getUrl: ({decisionDefinitionKey}) => `/${API_VERSION}/decision-definitions/${decisionDefinitionKey}/xml`,
};

const evaluateDecision: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-definitions/evaluation`,
};

export {
	decisionDefinitionSchema,
	queryDecisionDefinitionsRequestBodySchema,
	queryDecisionDefinitionsResponseBodySchema,
	getDecisionDefinitionXmlResponseBodySchema,
	evaluatedDecisionInputItemSchema,
	evaluatedDecisionOutputItemSchema,
	matchedDecisionRuleItemSchema,
	evaluatedDecisionResultSchema,
	evaluateDecisionRequestBodySchema,
	evaluateDecisionResponseBodySchema,
	queryDecisionDefinitions,
	getDecisionDefinition,
	getDecisionDefinitionXml,
	evaluateDecision,
};

export type {
	DecisionDefinition,
	QueryDecisionDefinitionsRequestBody,
	QueryDecisionDefinitionsResponseBody,
	GetDecisionDefinitionXmlResponseBody,
	EvaluatedDecisionInputItem,
	EvaluatedDecisionOutputItem,
	MatchedDecisionRuleItem,
	EvaluatedDecisionResult,
	EvaluateDecisionRequestBody,
	EvaluateDecisionResponseBody,
};
