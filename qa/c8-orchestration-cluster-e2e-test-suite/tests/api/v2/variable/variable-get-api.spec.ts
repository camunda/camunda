/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {setupVariableTest} from '../../../../utils/requestHelpers/variable-requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Get Variable API Tests', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/process_with_variables.bpmn']);
  });

  test('Get Variable Success', async ({request}) => {
    const variableSetup = await setupVariableTest(request);

    await test.step('Get Variable by key', async () => {
      let json: {[key: string]: unknown} = {};
      await expect(async () => {
        const variableKey = variableSetup.variableKey;
        const res = await request.get(
          buildUrl('/variables/{variableKey}', {variableKey}),
          {
            headers: jsonHeaders(),
          },
        );

        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/variables/{variableKey}',
            method: 'GET',
            status: '200',
          },
          res,
        );
        json = await res.json();
      }).toPass(defaultAssertionOptions);

      expect(json.variableKey).toBe(variableSetup.variableKey);
      expect(json.processInstanceKey).toBe(variableSetup.processInstanceKey);
      expect(json.name).toBe('customerId');
      expect(json.value).toBe('"CUST-123"');
    });

    await cancelProcessInstance(variableSetup.processInstanceKey);
  });

  test('Get Variable Not Found', async ({request}) => {
    const unknownVariableKey = '9999999999999999';
    const res = await request.get(
      buildUrl('/variables/{variableKey}', {variableKey: unknownVariableKey}),
      {
        headers: jsonHeaders(),
      },
    );

    await assertNotFoundRequest(
      res,
      `Variable with key '${unknownVariableKey}' not found`,
    );
  });

  test('Get Variable Invalid Key', async ({request}) => {
    const invalidVariableKey = 'invalidKey123';
    const res = await request.get(
      buildUrl('/variables/{variableKey}', {variableKey: invalidVariableKey}),
      {
        headers: jsonHeaders(),
      },
    );

    await assertBadRequest(res, invalidVariableKey);
  });

  test('Get Variable Unauthorized', async ({request}) => {
    const variableSetup = await setupVariableTest(request);

    await test.step('Get Variable without auth', async () => {
      const res = await request.get(
        buildUrl('/variables/{variableKey}', {
          variableKey: variableSetup.variableKey,
        }),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );

      await assertUnauthorizedRequest(res);
    });

    await cancelProcessInstance(variableSetup.processInstanceKey);
  });
});
