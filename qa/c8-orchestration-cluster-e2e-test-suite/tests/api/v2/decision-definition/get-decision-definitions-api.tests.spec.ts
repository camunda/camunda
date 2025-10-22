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
  assertRequiredFields,
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertEqualsForKeys,
  assertBadRequest,
  textXMLHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {decisionDefinitionRequiredFields} from '../../../../utils/beans/requestBeans';
import {
  DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT,
  deployDecisionAndStoreResponse,
} from '@requestHelpers';
import {DecisionDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import fs from 'fs';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get Decision Definitions API Tests', () => {
  const state: Record<string, unknown> = {};
  let decisionDefinition1: DecisionDeployment;
  let expectedBody1: Record<string, unknown>;

  test.beforeAll(async () => {
    await deployDecisionAndStoreResponse(
      state,
      '1',
      './resources/simpleDecisionTable1.dmn',
    );
    await deployDecisionAndStoreResponse(
      state,
      '2',
      './resources/simpleDecisionTable2.dmn',
    );
    decisionDefinition1 = state['decisionDefinition1'] as DecisionDeployment;
    expectedBody1 =
      DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT(decisionDefinition1);
  });

  test('Get Decision Definition', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}', {
          decisionDefinitionKey:
            decisionDefinition1.decisionDefinitionKey as string,
        }),
        {headers: jsonHeaders()},
      );

      expect(res.status()).toBe(200);
      const json = await res.json();
      assertRequiredFields(json, decisionDefinitionRequiredFields);
      assertEqualsForKeys(
        json,
        expectedBody1,
        decisionDefinitionRequiredFields,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Decision Definition Not Found', async ({request}) => {
    const nonExistentKey = '11111';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}', {
        decisionDefinitionKey: nonExistentKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Decision Definition with key '${nonExistentKey}' not found`,
    );
  });

  test('Get Decision Definition Bad Request', async ({request}) => {
    const invalidKey = 'abc';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}', {
        decisionDefinitionKey: invalidKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertBadRequest(res, `Failed to convert 'decisionDefinitionKey'`);
  });

  test('Get Decision Definition Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}', {
        decisionDefinitionKey:
          decisionDefinition1.decisionDefinitionKey as string,
      }),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Decision Definition XML', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
          decisionDefinitionKey:
            decisionDefinition1.decisionDefinitionKey as string,
        }),
        {headers: textXMLHeaders()},
      );

      expect(res.status()).toBe(200);
      expect(await res.text()).toEqual(
        fs.readFileSync('./resources/simpleDecisionTable1.dmn', 'utf-8'),
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Get Decision Definition XML Not Found', async ({request}) => {
    const nonExistentKey = '11111';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
        decisionDefinitionKey: nonExistentKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertNotFoundRequest(
      res,
      `Decision Definition with key '${nonExistentKey}' not found`,
    );
  });

  test('Get Decision Definition XML Bad Request', async ({request}) => {
    const invalidKey = 'abc';
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
        decisionDefinitionKey: invalidKey,
      }),
      {headers: jsonHeaders()},
    );
    await assertBadRequest(res, `Failed to convert 'decisionDefinitionKey'`);
  });

  test('Get Decision Definition XML Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl('/decision-definitions/{decisionDefinitionKey}/xml', {
        decisionDefinitionKey:
          decisionDefinition1.decisionDefinitionKey as string,
      }),
      {headers: {}},
    );
    await assertUnauthorizedRequest(res);
  });
});
