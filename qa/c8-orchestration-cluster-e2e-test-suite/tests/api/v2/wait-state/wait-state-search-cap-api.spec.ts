/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {cancelProcessInstance} from '../../../../utils/zeebeClient';
import {buildUrl, jsonHeaders, assertStatusCode} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {extendedAssertionOptions} from '../../../../utils/constants';
import {
  WAIT_STATES_SEARCH_ENDPOINT,
  createProcessInstanceWithManyWaitingTokens,
} from '@requestHelpers';

// camunda/camunda#56239: verified live there's no 1000-item cap on this
// endpoint — default page size is 100, page.totalItems is always the true
// count, and page.limit above 1000 returns everything. Guards
// data-completeness for large single-element cardinality instead.

test.describe
  .parallel('Wait State Search — Large Single-Element Cardinality', () => {
  const processInstanceKeys: string[] = [];

  test.afterAll(async () => {
    for (const key of processInstanceKeys) {
      await cancelProcessInstance(key);
    }
  });

  test('returns every waiting token when the count is small', async ({
    request,
  }) => {
    const tokenCount = 50;
    const instance =
      await createProcessInstanceWithManyWaitingTokens(tokenCount);
    processInstanceKeys.push(instance.processInstanceKey);

    await expect(async () => {
      const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: instance.processInstanceKey,
            elementId: 'wait_task',
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: WAIT_STATES_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.items).toHaveLength(tokenCount);
      expect(body.page.totalItems).toBe(tokenCount);
    }).toPass(extendedAssertionOptions);
  });

  test('reports the true count and never errors when a single element has far more waiting tokens than the default page size', async ({
    request,
  }) => {
    const tokenCount = 1200;
    const instance =
      await createProcessInstanceWithManyWaitingTokens(tokenCount);
    processInstanceKeys.push(instance.processInstanceKey);

    const firstPage: Record<string, unknown> = {};
    await expect(async () => {
      const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: instance.processInstanceKey,
            elementId: 'wait_task',
          },
        },
      });
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: WAIT_STATES_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      expect(body.items).toHaveLength(100);
      expect(body.page.totalItems).toBe(tokenCount);
      expect(
        body.page.endCursor,
        `Received JSON: ${JSON.stringify(body)}`,
      ).toBeTruthy();
      firstPage.body = body;
    }).toPass(extendedAssertionOptions);

    const body = firstPage.body as {
      items: Array<{elementInstanceKey: string}>;
      page: {endCursor: string};
    };

    await test.step('every token is retrievable in one call via an explicit page.limit', async () => {
      const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: instance.processInstanceKey,
            elementId: 'wait_task',
          },
          page: {limit: tokenCount},
        },
      });
      await assertStatusCode(res, 200);
      const bodyAll = await res.json();
      expect(bodyAll.items).toHaveLength(tokenCount);
      expect(bodyAll.page.totalItems).toBe(tokenCount);
    });

    await test.step('the remainder beyond the first page is reachable via the cursor', async () => {
      const res = await request.post(buildUrl(WAIT_STATES_SEARCH_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: instance.processInstanceKey,
            elementId: 'wait_task',
          },
          page: {after: body.page.endCursor},
        },
      });
      await assertStatusCode(res, 200);
      const nextPageBody = await res.json();
      expect(nextPageBody.items.length).toBeGreaterThan(0);
      const firstPageKeys = new Set(
        body.items.map((item) => item.elementInstanceKey),
      );
      const overlap = nextPageBody.items.filter(
        (item: {elementInstanceKey: string}) =>
          firstPageKeys.has(item.elementInstanceKey),
      );
      expect(overlap).toHaveLength(0);
    });
  });
});
