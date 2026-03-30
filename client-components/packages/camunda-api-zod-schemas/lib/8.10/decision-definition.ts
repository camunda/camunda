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
	decisionDefinitionResultSchema,
	decisionDefinitionSearchQuerySchema,
	decisionDefinitionSearchQueryResultSchema,
	evaluatedDecisionInputItemSchema,
	evaluatedDecisionOutputItemSchema,
	matchedDecisionRuleItemSchema,
	evaluatedDecisionResultSchema,
	decisionEvaluationInstructionSchema,
	evaluateDecisionResultSchema,
	getDecisionDefinitionXML200Schema,
} from './gen';

const decisionDefinitionSchema = decisionDefinitionResultSchema;
type DecisionDefinition = z.infer<typeof decisionDefinitionSchema>;

const queryDecisionDefinitionsRequestBodySchema = decisionDefinitionSearchQuerySchema;
type QueryDecisionDefinitionsRequestBody = z.infer<typeof queryDecisionDefinitionsRequestBodySchema>;

const queryDecisionDefinitionsResponseBodySchema = decisionDefinitionSearchQueryResultSchema;
type QueryDecisionDefinitionsResponseBody = z.infer<typeof queryDecisionDefinitionsResponseBodySchema>;

const getDecisionDefinitionXmlResponseBodySchema = getDecisionDefinitionXML200Schema;
type GetDecisionDefinitionXmlResponseBody = z.infer<typeof getDecisionDefinitionXmlResponseBodySchema>;

const decisionEvaluatedInputItemSchema = evaluatedDecisionInputItemSchema;
type EvaluatedDecisionInputItem = z.infer<typeof decisionEvaluatedInputItemSchema>;

const decisionEvaluatedOutputItemSchema = evaluatedDecisionOutputItemSchema;
type EvaluatedDecisionOutputItem = z.infer<typeof decisionEvaluatedOutputItemSchema>;

const decisionMatchedRuleItemSchema = matchedDecisionRuleItemSchema;
type MatchedDecisionRuleItem = z.infer<typeof decisionMatchedRuleItemSchema>;

const decisionEvaluatedResultSchema = evaluatedDecisionResultSchema;
type EvaluatedDecisionResult = z.infer<typeof decisionEvaluatedResultSchema>;

const evaluateDecisionRequestBodySchema = decisionEvaluationInstructionSchema;
type EvaluateDecisionRequestBody = z.infer<typeof evaluateDecisionRequestBodySchema>;

const evaluateDecisionResponseBodySchema = evaluateDecisionResultSchema;
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
	decisionEvaluatedInputItemSchema as evaluatedDecisionInputItemSchema,
	decisionEvaluatedOutputItemSchema as evaluatedDecisionOutputItemSchema,
	decisionMatchedRuleItemSchema as matchedDecisionRuleItemSchema,
	decisionEvaluatedResultSchema as evaluatedDecisionResultSchema,
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
