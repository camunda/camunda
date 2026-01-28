/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {
  assertStatusCode,
  buildUrl,
  defaultHeaders,
  assertUnauthorizedRequest,
  jsonHeaders,
  assertNotFoundRequest,
  assertBadRequest,
} from '../../../../utils/http';
import {deployResourceAndGetMetadata} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Resource Delete API', () => {
  const resourceName = 'process_to_test_delete_process_definition.bpmn';

  test('Delete Resource - Success 200', async ({request}) => {
    const metadata = await deployResourceAndGetMetadata(
      request,
      resourceName,
      0,
    );
    const resourceKey = metadata.resourceKey;

    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey}),
      {
        headers: defaultHeaders(),
      },
    );

    await assertStatusCode(res, 200);
  });

  test('Delete Resource - Not Found 404', async ({request}) => {
    const nonExistentResourceKey = '2251799813733053';

    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {
        resourceKey: nonExistentResourceKey,
      }),
      {
        headers: defaultHeaders(),
      },
    );

    await assertNotFoundRequest(
      res,
      "Command 'DELETE' rejected with code 'NOT_FOUND': Expected to delete resource but no resource found with key `2251799813733053`",
    );
  });

  test('Delete Resource - Bad Request 400 - Invalid resourceKey Format', async ({
    request,
  }) => {
    const invalidResourceKey = 'invalid-string-key';

    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {
        resourceKey: invalidResourceKey,
      }),
      {
        headers: defaultHeaders(),
      },
    );

    await assertBadRequest(
      res,
      "Failed to convert 'resourceKey' with value: 'invalid-string-key'",
    );
  });

  test('Delete Resource - Bad Request 400 - Invalid operationReference in Body', async ({
    request,
  }) => {
    const metadata = await deployResourceAndGetMetadata(
      request,
      resourceName,
      0,
    );
    const resourceKey = metadata.resourceKey;

    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey}),
      {
        headers: jsonHeaders(),
        data: {
          operationReference: 'invalid-string-reference',
        },
      },
    );

    await assertBadRequest(
      res,
      'Request property [operationReference] cannot be parsed',
    );
  });

  test('Delete Resource - Unauthorized 401', async ({request}) => {
    const metadata = await deployResourceAndGetMetadata(
      request,
      resourceName,
      0,
    );
    const resourceKey = metadata.resourceKey;

    const res = await request.post(
      buildUrl('/resources/{resourceKey}/deletion', {resourceKey}),
      {
        headers: {},
      },
    );

    await assertUnauthorizedRequest(res);
  });
});
