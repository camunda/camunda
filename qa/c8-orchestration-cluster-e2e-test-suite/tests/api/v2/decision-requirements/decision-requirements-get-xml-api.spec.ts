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
  assertUnauthorizedRequest,
  assertBadRequest,
  assertStatusCode,
  assertNotFoundRequest,
  textXMLHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {deployMammalDecisionAndStoreResponse} from '@requestHelpers';
import {DecisionRequirementsDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import {readFileSync} from 'node:fs';

test.describe.parallel('Get XML Decision Requirements API Tests', () => {
  let decisionRequirements: DecisionRequirementsDeployment[] = [];
  let expectedXML: string;

  test.beforeAll(async () => {
    await deployMammalDecisionAndStoreResponse(decisionRequirements);
    const xmlPath = './resources/isMammal_.dmn';
    expectedXML = readFileSync(xmlPath, 'utf-8');
  });

  test('Get XML Decision Requirements - Success', async ({request}) => {
    const decisionRequirementToGet = decisionRequirements[0];
    const decisionRequirementsToGetKey =
      decisionRequirementToGet.decisionRequirementsKey;
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-requirements/${decisionRequirementsToGetKey}/xml`),
        {
          headers: textXMLHeaders(),
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.text();
      expect(body).toEqual(expectedXML);
    }).toPass(defaultAssertionOptions);
  });

  test('Get XML Decision Requirements - Not found', async ({request}) => {
    const someRandomNotExistingKey = '9999999999999999';
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-requirements/${someRandomNotExistingKey}/xml`),
        {
          headers: textXMLHeaders(),
        },
      );

      await assertNotFoundRequest(
        res,
        `Decision Requirements with key '${someRandomNotExistingKey}' not found`,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get XML Decision Requirements - Invalid Value', async ({request}) => {
    await expect(async () => {
      const someInvalidValue = 'meow';
      const res = await request.get(
        buildUrl(`/decision-requirements/${someInvalidValue}/xml`),
        {
          headers: textXMLHeaders(),
        },
      );

      await assertBadRequest(
        res,
        "Failed to convert 'decisionRequirementsKey' with value: 'meow'",
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get XML Decision Requirements - Unauthorized', async ({request}) => {
    const decisionRequirementToGet = decisionRequirements[0];
    const decisionRequirementsToGetKey =
      decisionRequirementToGet.decisionRequirementsKey;
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/decision-requirements/${decisionRequirementsToGetKey}/xml`),
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
