/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {expressionEvaluationRequestSchema, expressionEvaluationResultSchema} from './gen';

const evaluateExpressionRequestBodySchema = expressionEvaluationRequestSchema;
type EvaluateExpressionRequestBody = z.infer<typeof evaluateExpressionRequestBodySchema>;

const evaluateExpressionResponseBodySchema = expressionEvaluationResultSchema;
type EvaluateExpressionResponseBody = z.infer<typeof evaluateExpressionResponseBodySchema>;

const evaluateExpression: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/expression/evaluation`,
};

export {evaluateExpressionRequestBodySchema, evaluateExpressionResponseBodySchema, evaluateExpression};

export type {EvaluateExpressionRequestBody, EvaluateExpressionResponseBody};
