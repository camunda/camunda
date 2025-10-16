/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertStatusCode,
  buildUrl,
  assertUnauthorizedRequest,
  jsonHeaders,
  assertNotFoundRequest,
} from '../../../../utils/http';
import {
  deployResourceAndGetMetadata,
  ResourceMetadata,
} from '../../../../utils/requestHelpers';
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {validateResponseShape} from '../../../../json-body-assertions';

function validateResourceResponse(
  body: JSONDoc,
  expectedMetadata: ResourceMetadata,
): void {
  validateResponseShape(
    {
      path: '/resources/{resourceKey}',
      method: 'GET',
      status: '200',
    },
    body,
  );
  expect(body.resourceKey).toBe(expectedMetadata.resourceKey);
  expect(body.resourceName).toBe(expectedMetadata.resourceName);
  expect(body.tenantId).toEqual('<default>');
  expect(body.version).toBe(expectedMetadata.version);
  expect(body.resourceKey).toBeDefined();

  if (expectedMetadata.resourceId) {
    expect(body.resourceId).toBe(expectedMetadata.resourceId);
  }
}

test.describe.parallel('Resource Get API', () => {
  // eslint-disable-next-line playwright/expect-expect
  test('Get Resource - RPA Success 200', async ({request}) => {
    const resourceName = 'rpa_get_resource_api_test.rpa';
    const metadata = await deployResourceAndGetMetadata(
      request,
      resourceName,
      0,
    );

    const res = await request.get(
      buildUrl('/resources/{resourceKey}', {resourceKey: metadata.resourceKey}),
      {
        headers: jsonHeaders(),
      },
    );

    await assertStatusCode(res, 200);
    const body = await res.json();
    validateResourceResponse(body, metadata);
  });

  test('Get Resource - Not Found 404', async ({request}) => {
    const nonExistentResourceKey = '2251799813733053';

    const res = await request.get(
      buildUrl('/resources/{resourceKey}', {
        resourceKey: nonExistentResourceKey,
      }),
      {
        headers: jsonHeaders(),
      },
    );

    await assertNotFoundRequest(
      res,
      "Command 'FETCH' rejected with code 'NOT_FOUND': Expected to fetch resource but no resource found with key `2251799813733053`",
    );
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Resource - Unauthorized 401', async ({request}) => {
    const res = await request.get(
      buildUrl('/resources/{resourceKey}', {resourceKey: 'someKey'}),
      {
        headers: {},
      },
    );

    await assertUnauthorizedRequest(res);
  });
});
