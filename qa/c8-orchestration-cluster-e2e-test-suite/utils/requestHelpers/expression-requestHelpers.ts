/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
} from '../http';

export const EXPRESSION_URL = '/expression/evaluation';

export async function evaluateExpression(
  request: APIRequestContext,
  expression: string,
  variables: Record<string, unknown>,
) {
  return request.post(buildUrl(EXPRESSION_URL), {
    headers: jsonHeaders(),
    data: {expression, variables},
  });
}