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
  assertEqualsForKeys,
  assertRequiredFields,
  assertNotFoundRequest,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {
  deployMammalDecisionAndStoreResponse,
  deployTwoSimpleDecisionsAndStoreResponse,
} from '@requestHelpers';
import {DecisionRequirementsDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import {validateResponse} from '../../../../json-body-assertions';
import {decisionRequirementRequiredFields} from 'utils/beans/requestBeans';

test.describe.parallel('Get JSON Decision Requirements API Tests', () => {
  let decisionRequirements: DecisionRequirementsDeployment[] = [];

  test.beforeAll(async () => {
    await deployMammalDecisionAndStoreResponse(decisionRequirements);
    await deployTwoSimpleDecisionsAndStoreResponse(decisionRequirements);
  });

  test('Get JSON Decision Requirements - Success', async ({request}) => {
    const decisionRequirementToGet = decisionRequirements[0];
    const decisionRequirementsToGetKey =
      decisionRequirementToGet.decisionRequirementsKey;
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-requirements/${decisionRequirementsToGetKey}`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/decision-requirements/{decisionRequirementsKey}',
          method: 'GET',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      assertRequiredFields(body, decisionRequirementRequiredFields);
      assertEqualsForKeys(
        decisionRequirementToGet,
        body,
        decisionRequirementRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get JSON Decision Requirements - Not found', async ({request}) => {
    const someRandomNotExistingKey = '9999999999999999';
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-requirements/${someRandomNotExistingKey}`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertNotFoundRequest(
        res,
        `Decision Requirements with key '${someRandomNotExistingKey}' not found`,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get JSON Decision Requirements - Invalid Value', async ({request}) => {
    await expect(async () => {
      const someInvalidValue = 'meow';
      const res = await request.get(
        buildUrl(`/decision-requirements/${someInvalidValue}`),
        {
          headers: jsonHeaders(),
        },
      );

      await assertBadRequest(
        res,
        "Failed to convert 'decisionRequirementsKey' with value: 'meow'",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get JSON Decision Requirements - Unauthorized', async ({request}) => {
    const decisionRequirementToGet = decisionRequirements[1];
    const decisionRequirementsToGetKey =
      decisionRequirementToGet.decisionRequirementsKey;
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-requirements/${decisionRequirementsToGetKey}`),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });
});
