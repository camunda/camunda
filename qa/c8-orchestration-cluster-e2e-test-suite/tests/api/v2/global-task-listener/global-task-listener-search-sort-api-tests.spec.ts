/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {
  jsonHeaders,
  buildUrl,
  assertUnauthorizedRequest,
  assertBadRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {
  generateUniqueId,
  defaultAssertionOptions,
} from '../../../../utils/constants';
import {validateResponse} from '../../../../json-body-assertions';
import {cleanupGlobalTaskListeners} from '../../../../utils/globalTaskListenerCleanup';
import {createGlobalTaskListener} from '@requestHelpers';

type GlobalTaskListenerItem = {
  id: string;
  type: string;
  eventTypes: string[];
  priority: number | null;
  afterNonGlobal: boolean | null;
  retries: number | null;
  source: string;
};

/* eslint-disable playwright/expect-expect */
test.describe.serial('Global Task Listener API Tests - Search and Sort', () => {
  // Use a shared prefix to isolate listeners created by this test suite
  const prefix = `sort-test-${generateUniqueId()}`;

  // Listeners with distinct priorities and IDs for predictable ordering
  const listeners = [
    {
      id: `${prefix}-c`,
      type: `io.camunda.test.listener.${prefix}-c`,
      eventTypes: ['creating'],
      priority: 30,
    },
    {
      id: `${prefix}-a`,
      type: `io.camunda.test.listener.${prefix}-a`,
      eventTypes: ['completing'],
      priority: 10,
    },
    {
      id: `${prefix}-b`,
      type: `io.camunda.test.listener.${prefix}-b`,
      eventTypes: ['assigning'],
      priority: 20,
    },
  ];

  test.beforeAll(async ({request}) => {
    for (const listener of listeners) {
      await createGlobalTaskListener(request, listener);
    }
  });

  test.afterAll(async ({request}) => {
    await cleanupGlobalTaskListeners(
      request,
      listeners.map((l) => l.id),
    );
  });

  test('Search Global Task Listeners - sort by priority ASC', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/global-task-listeners/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {id: {$in: listeners.map((l) => l.id)}},
            sort: [{field: 'priority', order: 'ASC'}],
          },
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/global-task-listeners/search', method: 'POST', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body.items.length).toBe(3);

      const priorities = body.items.map(
        (item: GlobalTaskListenerItem) => item.priority,
      );
      for (let i = 1; i < priorities.length; i++) {
        expect(priorities[i - 1]).toBeLessThanOrEqual(priorities[i]);
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Search Global Task Listeners - sort by priority DESC', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/global-task-listeners/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {id: {$in: listeners.map((l) => l.id)}},
            sort: [{field: 'priority', order: 'DESC'}],
          },
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/global-task-listeners/search', method: 'POST', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body.items.length).toBe(3);

      const priorities = body.items.map(
        (item: GlobalTaskListenerItem) => item.priority,
      );
      for (let i = 1; i < priorities.length; i++) {
        expect(priorities[i - 1]).toBeGreaterThanOrEqual(priorities[i]);
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Search Global Task Listeners - sort by id ASC', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/global-task-listeners/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {id: {$in: listeners.map((l) => l.id)}},
            sort: [{field: 'id', order: 'ASC'}],
          },
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/global-task-listeners/search', method: 'POST', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body.items.length).toBe(3);

      const ids = body.items.map((item: GlobalTaskListenerItem) => item.id);
      // IDs end in -a, -b, -c so lexicographic ASC order is deterministic
      expect(ids[0]).toBe(`${prefix}-a`);
      expect(ids[1]).toBe(`${prefix}-b`);
      expect(ids[2]).toBe(`${prefix}-c`);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Global Task Listeners - sort by id DESC', async ({request}) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/global-task-listeners/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {id: {$in: listeners.map((l) => l.id)}},
            sort: [{field: 'id', order: 'DESC'}],
          },
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/global-task-listeners/search', method: 'POST', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body.items.length).toBe(3);

      const ids = body.items.map((item: GlobalTaskListenerItem) => item.id);
      expect(ids[0]).toBe(`${prefix}-c`);
      expect(ids[1]).toBe(`${prefix}-b`);
      expect(ids[2]).toBe(`${prefix}-a`);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Global Task Listeners - sort by priority ASC then id ASC (compound sort)', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl('/global-task-listeners/search'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {id: {$in: listeners.map((l) => l.id)}},
            sort: [
              {field: 'priority', order: 'ASC'},
              {field: 'id', order: 'ASC'},
            ],
          },
        },
      );

      await assertStatusCode(res, 200);
      await validateResponse(
        {path: '/global-task-listeners/search', method: 'POST', status: '200'},
        res,
      );
      const body = await res.json();
      expect(body.items.length).toBe(3);

      // priority 10 → -a, priority 20 → -b, priority 30 → -c
      expect(body.items[0].id).toBe(`${prefix}-a`);
      expect(body.items[0].priority).toBe(10);
      expect(body.items[1].id).toBe(`${prefix}-b`);
      expect(body.items[1].priority).toBe(20);
      expect(body.items[2].id).toBe(`${prefix}-c`);
      expect(body.items[2].priority).toBe(30);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Global Task Listeners - invalid sort field returns 400', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/global-task-listeners/search'), {
      headers: jsonHeaders(),
      data: {
        sort: [{field: 'invalidField', order: 'ASC'}],
      },
    });

    await assertBadRequest(res, /invalidField/i);
  });

  test('Search Global Task Listeners - missing sort field returns 400', async ({
    request,
  }) => {
    const res = await request.post(buildUrl('/global-task-listeners/search'), {
      headers: jsonHeaders(),
      data: {
        sort: [{order: 'ASC'}],
      },
    });

    await assertBadRequest(res, /field|sort/i, 'INVALID_ARGUMENT');
  });

  test('Search Global Task Listeners - unauthorized', async ({request}) => {
    const res = await request.post(buildUrl('/global-task-listeners/search'), {
      headers: {},
      data: {},
    });

    await assertUnauthorizedRequest(res);
  });
});
