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
  assertNotFoundRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {
  generateUniqueId,
  defaultAssertionOptions,
} from '../../../../utils/constants';

async function createGlobalTaskListener(
  request: import('@playwright/test').APIRequestContext,
) {
  const id = `test-gl-${generateUniqueId()}`;
  const body = {
    id,
    type: `io.camunda.test.listener.${id}`,
    eventTypes: ['creating', 'completing'],
  };

  await expect(async () => {
    const res = await request.post(buildUrl('/global-task-listeners'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertStatusCode(res, 201);
  }).toPass(defaultAssertionOptions);

  return body;
}

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Global Task Listener API Tests - Delete', () => {
  const createdListenerIds: string[] = [];

  test.afterAll(async ({request}) => {
    for (const id of createdListenerIds) {
      try {
        await request.delete(buildUrl('/global-task-listeners/{id}', {id}), {
          headers: jsonHeaders(),
        });
      } catch {
        // Ignore cleanup errors
      }
    }
  });

  test('Delete Global Task Listener - success', async ({request}) => {
    const created = await createGlobalTaskListener(request);

    // Delete the listener
    const deleteRes = await request.delete(
      buildUrl('/global-task-listeners/{id}', {id: created.id}),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(deleteRes, 204);

    // Verify it no longer exists
    await expect(async () => {
      const getRes = await request.get(
        buildUrl('/global-task-listeners/{id}', {id: created.id}),
        {headers: jsonHeaders()},
      );
      await assertNotFoundRequest(getRes, created.id);
    }).toPass(defaultAssertionOptions);
  });

  test('Delete Global Task Listener - unauthorized', async ({request}) => {
    const created = await createGlobalTaskListener(request);
    createdListenerIds.push(created.id);

    const res = await request.delete(
      buildUrl('/global-task-listeners/{id}', {id: created.id}),
      {headers: {}},
    );

    await assertUnauthorizedRequest(res);
  });

  test('Delete Global Task Listener - not found', async ({request}) => {
    const res = await request.delete(
      buildUrl('/global-task-listeners/{id}', {id: 'non-existent-id'}),
      {headers: jsonHeaders()},
    );

    await assertNotFoundRequest(res, 'non-existent-id');
  });

  test('Delete Global Task Listener - already deleted returns not found', async ({
    request,
  }) => {
    const created = await createGlobalTaskListener(request);

    // First delete should succeed
    const firstDelete = await request.delete(
      buildUrl('/global-task-listeners/{id}', {id: created.id}),
      {headers: jsonHeaders()},
    );
    await assertStatusCode(firstDelete, 204);

    // Second delete on the same id should return 404
    await expect(async () => {
      const secondDelete = await request.delete(
        buildUrl('/global-task-listeners/{id}', {id: created.id}),
        {headers: jsonHeaders()},
      );
      await assertNotFoundRequest(secondDelete, created.id);
    }).toPass(defaultAssertionOptions);
  });
});
