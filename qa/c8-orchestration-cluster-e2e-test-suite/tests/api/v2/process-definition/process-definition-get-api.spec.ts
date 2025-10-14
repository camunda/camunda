/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {createInstances, deploy} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponseShape} from '../../../../json-body-assertions';
import {defaultAssertionOptions} from '../../../../utils/constants';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Process Definition Get API', () => {
  const state: Record<string, unknown> = {};
  test.beforeAll(async () => {
    await deploy(['./resources/process_definition_api_tests.bpmn']);

    await createInstances('process_definition_api_tests', 1, 1).then(
      (instances) => {
        state['processDefinitionKey'] = instances[0].processDefinitionKey;
        state['processDefinitionId'] = instances[0].processDefinitionId;
      },
    );
  });

  test('Get Process Definition - Success', async ({request}) => {
    await expect(async () => {
      const res = await request.get(
        buildUrl(`/process-definitions/${state.processDefinitionKey}`),
        {headers: jsonHeaders()},
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      validateResponseShape(
        {
          path: '/process-definitions/{processDefinitionKey}',
          method: 'GET',
          status: '200',
        },
        body,
      );
      expect(body.processDefinitionKey).toBe(state.processDefinitionKey);
      expect(body.name).toBe('Process Definition API Tests');
      expect(body.resourceName).toBe('process_definition_api_tests.bpmn');
      expect(body.version).toBe(1);
      expect(body.processDefinitionId).toBe(state.processDefinitionId);
      expect(body.tenantId).toBe('<default>');
      expect(body.hasStartForm).toBeFalsy();
    }).toPass(defaultAssertionOptions);
  });

  test('Get Process Definition - Not Found', async ({request}) => {
    const res = await request.get(buildUrl(`/process-definitions/123456`), {
      headers: jsonHeaders(),
    });
    await assertNotFoundRequest(
      res,
      "Process Definition with key '123456' not found",
    );
  });

  test('Get Process Definition - Unauthorized', async ({request}) => {
    const res = await request.get(
      buildUrl(`/process-definitions/${state.processDefinitionKey}`),
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get Process Definition - Invalid Key', async ({request}) => {
    const res = await request.get(buildUrl(`/process-definitions/invalidKey`), {
      headers: {...jsonHeaders()},
    });
    await assertBadRequest(
      res,
      "Failed to convert 'processDefinitionKey' with value: 'invalidKey'",
    );
  });
});
