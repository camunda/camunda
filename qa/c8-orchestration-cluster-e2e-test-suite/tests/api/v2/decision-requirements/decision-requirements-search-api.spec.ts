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
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {deployMammalDecisionAndStoreResponse} from '@requestHelpers';
import {DecisionRequirementsDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import {validateResponse} from '../../../../json-body-assertions';
import { decisionRequirementRequiredFields } from 'utils/beans/requestBeans';

const DECISION_REQUIREMENTS_SEARCH_ENDPOINT = '/decision-requirements/search';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Search Decision Requirements API Tests', () => {
  let decisionRequirements: DecisionRequirementsDeployment[] = [];

  test.beforeAll(async () => {
    await deployMammalDecisionAndStoreResponse(
      decisionRequirements,
    );
  });

  test('Search decision requirements - 1 result - success', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(DECISION_REQUIREMENTS_SEARCH_ENDPOINT, {}),
        {
          headers: jsonHeaders(),
          data: {},
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {
        path: DECISION_REQUIREMENTS_SEARCH_ENDPOINT,
        method: 'POST',
        status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.items.length).toBeGreaterThanOrEqual(1);
      expect(Array.isArray(body.items)).toBe(true);
    }).toPass(defaultAssertionOptions);
  });

  test('Search decision requirements by decisionRequirementsName success', async ({request}) => {
    const decisionRequirementToSearch = decisionRequirements[0];
    await expect(async () => {
      const res = await request.post(buildUrl(DECISION_REQUIREMENTS_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            decisionRequirementsName: decisionRequirementToSearch.decisionRequirementsName,
          },
        },
      });

      await assertStatusCode(res, 200);

      await validateResponse(
        {
        path: DECISION_REQUIREMENTS_SEARCH_ENDPOINT,
        method: 'POST',
        status: '200',
        },
        res,
      );

      const body = await res.json();
      assertRequiredFields(body, ['items', 'page']);
      expect(body.items.length).toEqual(1);
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      assertEqualsForKeys(decisionRequirementToSearch, body.items[0], decisionRequirementRequiredFields);
    }).toPass(defaultAssertionOptions);
  });
});