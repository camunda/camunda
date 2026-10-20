/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {decisionDefinitionResultSchema} from './gen/zod/decisionDefinitionResultSchema';
import {decisionDefinitionSearchQueryResultSchema} from './gen/zod/decisionDefinitionSearchQueryResultSchema';
import {decisionDefinitionSearchQuerySchema} from './gen/zod/decisionDefinitionSearchQuerySchema';
import {decisionEvaluationInstructionSchema} from './gen/zod/decisionEvaluationInstructionSchema';
import {evaluateDecisionResultSchema} from './gen/zod/evaluateDecisionResultSchema';
import {evaluatedDecisionInputItemSchema as genEvaluatedDecisionInputItemSchema} from './gen/zod/evaluatedDecisionInputItemSchema';
import {evaluatedDecisionOutputItemSchema as genEvaluatedDecisionOutputItemSchema} from './gen/zod/evaluatedDecisionOutputItemSchema';
import {evaluatedDecisionResultSchema as genEvaluatedDecisionResultSchema} from './gen/zod/evaluatedDecisionResultSchema';
import {getDecisionDefinitionXMLStatus200Schema} from './gen/zod/getDecisionDefinitionXMLSchema';
import {matchedDecisionRuleItemSchema as genMatchedDecisionRuleItemSchema} from './gen/zod/matchedDecisionRuleItemSchema';
import type {DecisionDefinitionResult} from './gen/types/DecisionDefinitionResult';
import type {DecisionDefinitionSearchQuery} from './gen/types/DecisionDefinitionSearchQuery';
import type {DecisionDefinitionSearchQueryResult} from './gen/types/DecisionDefinitionSearchQueryResult';
import type {DecisionEvaluationInstruction} from './gen/types/DecisionEvaluationInstruction';
import type {EvaluateDecisionResult} from './gen/types/EvaluateDecisionResult';
import type {EvaluatedDecisionInputItem as GenEvaluatedDecisionInputItem} from './gen/types/EvaluatedDecisionInputItem';
import type {EvaluatedDecisionOutputItem as GenEvaluatedDecisionOutputItem} from './gen/types/EvaluatedDecisionOutputItem';
import type {EvaluatedDecisionResult as GenEvaluatedDecisionResult} from './gen/types/EvaluatedDecisionResult';
import type {GetDecisionDefinitionXMLStatus200} from './gen/types/GetDecisionDefinitionXML';
import type {MatchedDecisionRuleItem as GenMatchedDecisionRuleItem} from './gen/types/MatchedDecisionRuleItem';

const decisionDefinitionSchema = decisionDefinitionResultSchema;
type DecisionDefinition = DecisionDefinitionResult;

const queryDecisionDefinitionsRequestBodySchema = decisionDefinitionSearchQuerySchema;
type QueryDecisionDefinitionsRequestBody = DecisionDefinitionSearchQuery;

const queryDecisionDefinitionsResponseBodySchema = decisionDefinitionSearchQueryResultSchema;
type QueryDecisionDefinitionsResponseBody = DecisionDefinitionSearchQueryResult;

const getDecisionDefinitionXmlResponseBodySchema = getDecisionDefinitionXMLStatus200Schema;
type GetDecisionDefinitionXmlResponseBody = GetDecisionDefinitionXMLStatus200;

const evaluatedDecisionInputItemSchema = genEvaluatedDecisionInputItemSchema;
type EvaluatedDecisionInputItem = GenEvaluatedDecisionInputItem;

const evaluatedDecisionOutputItemSchema = genEvaluatedDecisionOutputItemSchema;
type EvaluatedDecisionOutputItem = GenEvaluatedDecisionOutputItem;

const matchedDecisionRuleItemSchema = genMatchedDecisionRuleItemSchema;
type MatchedDecisionRuleItem = GenMatchedDecisionRuleItem;

const evaluatedDecisionResultSchema = genEvaluatedDecisionResultSchema;
type EvaluatedDecisionResult = GenEvaluatedDecisionResult;

const evaluateDecisionRequestBodySchema = decisionEvaluationInstructionSchema;
type EvaluateDecisionRequestBody = DecisionEvaluationInstruction;

const evaluateDecisionResponseBodySchema = evaluateDecisionResultSchema;
type EvaluateDecisionResponseBody = EvaluateDecisionResult;

const queryDecisionDefinitions = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-definitions/search` as const,
} as const satisfies Endpoint;

const getDecisionDefinition = {
	method: 'GET',
	getUrl: ({decisionDefinitionKey}) => `/${API_VERSION}/decision-definitions/${decisionDefinitionKey}` as const,
} as const satisfies Endpoint<{decisionDefinitionKey: string}>;

const getDecisionDefinitionXml = {
	method: 'GET',
	getUrl: ({decisionDefinitionKey}) => `/${API_VERSION}/decision-definitions/${decisionDefinitionKey}/xml` as const,
} as const satisfies Endpoint<{decisionDefinitionKey: string}>;

const evaluateDecision = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-definitions/evaluation` as const,
} as const satisfies Endpoint;

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
