/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {conditionalEvaluationInstructionSchema, evaluateConditionalResultSchema} from './gen';

const evaluateConditionalsRequestBodySchema = conditionalEvaluationInstructionSchema;
type EvaluateConditionalsRequestBody = z.infer<typeof evaluateConditionalsRequestBodySchema>;

const evaluateConditionalsResponseBodySchema = evaluateConditionalResultSchema;
type EvaluateConditionalsResponseBody = z.infer<typeof evaluateConditionalsResponseBodySchema>;

const evaluateConditionals: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/conditionals/evaluation`,
};

export {evaluateConditionalsRequestBodySchema, evaluateConditionalsResponseBodySchema, evaluateConditionals};

export type {EvaluateConditionalsRequestBody, EvaluateConditionalsResponseBody};
