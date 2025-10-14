/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertBadRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {validateResponseShape} from '../../../../json-body-assertions';
import {createInstances, deploy} from '../../../../utils/zeebeClient';
import {defaultAssertionOptions} from '../../../../utils/constants';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Process Definition Search API', () => {
  const state: Record<string, unknown> = {};
  test.beforeAll(async () => {
    await deploy([
      './resources/process_definition_api_tests.bpmn',
      './resources/process_definition_api_tests_2.bpmn',
    ]);
    await createInstances('process_definition_api_tests', 1, 1).then(
      (instances) => {
        state['processDefinitionKey'] = instances[0].processDefinitionKey;
        state['processDefinitionId'] = instances[0].processDefinitionId;
      },
    );
  });

  test('Search Process Definitions - Basic', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/process-definitions/search'), {
        headers: jsonHeaders(),
        data: {}, // empty body for basic search
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      validateResponseShape(
        {
          path: '/process-definitions/search',
          method: 'POST',
          status: '200',
        },
        body,
      );
      expect(body.page.totalItems).toBeGreaterThan(1);
      expect(body.items.length).toBeGreaterThan(1);
      expect(body.page.totalItems).toBe(body.items.length);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Process Definitions - with one filter field', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/process-definitions/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: state.processDefinitionId,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(body.items.length);
      expect(body.page.totalItems).toBe(1);
      expect(body.items[0].processDefinitionId).toBe(state.processDefinitionId);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Process Definitions - with multiple filter', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/process-definitions/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            version: 1,
            processDefinitionKey: state.processDefinitionKey,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(body.items.length);
      expect(body.page.totalItems).toBe(1);
      expect(body.items[0].version).toBe(1);
      expect(body.items[0].processDefinitionKey).toBe(
        state.processDefinitionKey,
      );
      expect(body.items[0].processDefinitionId).toBe(state.processDefinitionId);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Process Definitions - filter isLatestVersion & resourceName', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/process-definitions/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            isLatestVersion: true,
            resourceName: 'process_definition_api_tests_2.bpmn',
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(body.items.length);
      expect(body.page.totalItems).toBe(1);
      expect(body.items[0].version).toBe(1);
      expect(body.items[0].processDefinitionId).toBe(
        'process_definition_api_tests_2',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Process Definitions - with empty result', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/process-definitions/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: 'nonExistingProcessDefinitionId',
            processDefinitionKey: '123456789',
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBe(0);
      expect(body.items.length).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Process Definitions - with pagination', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/process-definitions/search'), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: 1,
          },
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThan(1);
      expect(body.items.length).toBe(1);
    }).toPass(defaultAssertionOptions);
  });

  //Skipped due to bug 39372: https://github.com/camunda/camunda/issues/39372
  test.skip('Search Process Definitions - with invalid pagination parameters', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-definitions/search'), {
      headers: jsonHeaders(),
      data: {
        page: {
          limit: -1,
        },
      },
    });
    await assertBadRequest(res, 'limit must be a positive number');
  });

  test('Search Process Definitions - with sorting', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/process-definitions/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'processDefinitionKey',
              order: 'DESC',
            },
          ],
        },
      });
      await assertStatusCode(res, 200);
      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThan(0);
      expect(body.items.length).toBeGreaterThan(0);
      expect(body.page.totalItems).toBe(body.items.length);
      for (let i = 1; i < body.items.length; i++) {
        expect(
          BigInt(body.items[i - 1].processDefinitionKey),
        ).toBeGreaterThanOrEqual(BigInt(body.items[i].processDefinitionKey));
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Search Process Definitions - with missing required sorting field', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-definitions/search'), {
      headers: jsonHeaders(),
      data: {
        sort: [
          {
            order: 'DESC',
          },
        ],
      },
    });
    await assertBadRequest(
      res,
      'Sort field must not be null.',
      'INVALID_ARGUMENT',
    );
  });

  test('Search Process Definitions - with invalid sorting field', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/process-definitions/search'), {
      headers: jsonHeaders(),
      data: {
        sort: [
          {
            field: 'invalidField',
            order: 'DESC',
          },
        ],
      },
    });
    await assertBadRequest(
      res,
      "Unexpected value 'invalidField' for enum field 'field'. Use any of the following values: [processDefinitionKey, name, resourceName, version, versionTag, processDefinitionId, tenantId]",
    );
  });

  test('Search Process Definitions - Unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/process-definitions/search'), {
      // No authentication headers
      data: {},
    });
    await assertUnauthorizedRequest(res);
  });
});
