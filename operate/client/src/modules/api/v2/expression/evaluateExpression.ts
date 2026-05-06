/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';

type EvaluateExpressionRequest = {expression: string} & (
  | {processInstanceKey: string; elementInstanceKey?: never}
  | {elementInstanceKey: string; processInstanceKey?: never}
);

type EvaluateExpressionWarning = {
  message: string;
};

type EvaluateExpressionResponse = {
  expression: string;
  result: unknown;
  warnings: EvaluateExpressionWarning[];
};

const evaluateExpression = async (request: EvaluateExpressionRequest) => {
  return requestWithThrow<EvaluateExpressionResponse>({
    url: `/v2/expression/evaluation`,
    method: `POST`,
    body: JSON.stringify(request),
  });
};

export {evaluateExpression};

export type {
  EvaluateExpressionRequest,
  EvaluateExpressionResponse,
  EvaluateExpressionWarning,
};
