/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertUnauthorizedRequest,
  assertBadRequest,
  assertStatusCode,
  assertRequiredFields,
  assertEqualsForKeys,
  assertNotFoundRequest,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  createMammalProcessInstanceAndDeployMammalDecision,
  DecisionInstance,
} from '@requestHelpers';
import {validateResponse} from '../../../../json-body-assertions';
import {
  getDecisionInstanceResponseRequiredFields,
  decisionInstanceRequiredFields,
} from 'utils/beans/requestBeans';

test.describe.parallel('Search Decision Instances API Tests', () => {
  let decisionInstances: DecisionInstance[] = [];
  let processInstanceKey: string;

  test.beforeAll(async ({request}) => {
    const result =
      await createMammalProcessInstanceAndDeployMammalDecision(request);
    processInstanceKey = result.instance.processInstanceKey;
    decisionInstances = result.decisions;
  });

  test('Get Decision Instance - Success', async ({request}) => {
    const decisionInstanceToGet = decisionInstances[0];
    const decisionEvaluationInstanceKeyToGet =
      decisionInstanceToGet.decisionEvaluationInstanceKey;

    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-instances/${decisionEvaluationInstanceKeyToGet}`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/decision-instances/{decisionEvaluationInstanceKey}',
          method: 'GET',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.decisionEvaluationInstanceKey).toBe(
        decisionEvaluationInstanceKeyToGet,
      );
      expect(body.processInstanceKey).toBe(processInstanceKey);
      assertRequiredFields(body, getDecisionInstanceResponseRequiredFields);
      assertEqualsForKeys(
        decisionInstanceToGet,
        body,
        decisionInstanceRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Decision Instance - Not found', async ({request}) => {
    const someRandomNotExistingKey = '9999999999999999';
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-instances/${someRandomNotExistingKey}`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertNotFoundRequest(
        res,
        `Decision Instance with id '${someRandomNotExistingKey}' not found`,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Decision Instances - Unauthorized', async ({request}) => {
    const decisionInstanceToGet = decisionInstances[0];
    const decisionEvaluationInstanceKeyToGet =
      decisionInstanceToGet.decisionEvaluationInstanceKey;
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-instances/${decisionEvaluationInstanceKeyToGet}`),
        {
          headers: {
            'Content-Type': 'application/json',
          },
          data: {},
        },
      );

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });
});
