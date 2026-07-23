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

// camunda/camunda#56239: `wait-states/search` was found to silently truncate
// results for a single element with a large number of concurrently active
// tokens (no indication anything was cut). It was resolved by adding a
// dedicated aggregated statistics endpoint
// (GET /process-instances/{key}/statistics/wait-states, thoroughly covered
// by dev's own ProcessInstanceWaitStateStatisticsIT — do not re-test that
// here) for the diagram overlay count.
//
// Verified live against a real cluster before writing these assertions
// (an earlier draft of this spec assumed the issue's "capped at 1000"
// wording described `wait-states/search` itself — it doesn't): the default
// page size is the standard 100, `page.totalItems` always reports the true
// match count (not capped), and an explicit `page.limit` well above 1000
// returns every item with no server-side ceiling. So the actual regression
// to guard is data-completeness for a large single-element cardinality:
// nothing errors, the true count is reported, and every token is reachable
// either via an explicit limit or by paging through with the cursor.

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
      // The default page size is the standard 100 — not the full 1200 —
      // but totalItems must still report the true, uncapped count so the
      // caller knows there is more to fetch.
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
