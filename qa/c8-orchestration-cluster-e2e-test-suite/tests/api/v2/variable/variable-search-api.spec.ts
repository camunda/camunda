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
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {setupVariableTest} from '../../../../utils/requestHelpers/variable-requestHelpers';

test.describe.parallel('Search Variables API Tests', () => {
  const processInstanceKeys: string[] = [];

  test.beforeAll(async () => {
    await deploy(['./resources/process_with_variables.bpmn']);
  });

  test.afterAll(async () => {
    for (const key of processInstanceKeys) {
      await cancelProcessInstance(key);
    }
  });

  test('Search Variables Success', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await setupVariableTest(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {},
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/variables/search',
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(Array.isArray(body.items)).toBe(true);
      expect(body.items.length).toBeGreaterThan(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Variables With Name Filter', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await setupVariableTest(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);

    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            name: 'customerId',
          },
        },
      });

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/variables/search',
          method: 'POST',
          status: '200',
        },
        res,
      );

      const body = await res.json();
      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toBeGreaterThan(0);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.name).toBe('customerId');
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Variables With Multiple Filters', async ({request}) => {
    const localState: Record<string, unknown> = {};
    await setupVariableTest(localState, request);
    processInstanceKeys.push(localState['processInstanceKey'] as string);

    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: localState['processInstanceKey'],
            name: 'customerId',
          },
        },
      });

      await assertStatusCode(res, 200);
      const body = await res.json();

      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toBeGreaterThan(0);
      body.items.forEach((item: Record<string, unknown>) => {
        expect(item.processInstanceKey).toBe(localState['processInstanceKey']);
        expect(item.name).toBe('customerId');
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Variables Pagination Limit 1', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: 1,
          },
        },
      });

      await assertStatusCode(res, 200);
      const body = await res.json();

      expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
      expect(body.items.length).toBe(1);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Variables Sort by Name ASC', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              field: 'name',
              order: 'ASC',
            },
          ],
        },
      });

      await assertStatusCode(res, 200);
      const body = await res.json();

      const names = body.items.map(
        (item: Record<string, unknown>) => item.name as string,
      );
      const sortedNames = [...names].sort();
      expect(names).toEqual(sortedNames);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Variables Unauthorized', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: {
          'Content-Type': 'application/json',
        },
        data: {},
      });

      await assertUnauthorizedRequest(res);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Variables Invalid Filter', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            invalidField: 'someValue',
          },
        },
      });

      await assertBadRequest(
        res,
        'Request property [filter.invalidField] cannot be parsed',
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Variables Invalid Sort Field', async ({request}) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          sort: [
            {
              // field omitted on purpose
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
    }).toPass(defaultAssertionOptions);
  });

  //Skipped due to bug 39372: https://github.com/camunda/camunda/issues/39372
  test.skip('Search Variables with invalid pagination parameters', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(buildUrl('/variables/search'), {
        headers: jsonHeaders(),
        data: {
          page: {
            limit: 0,
          },
        },
      });

      await assertBadRequest(res, /page.from|page.limit/);
    }).toPass(defaultAssertionOptions);
  });
});
