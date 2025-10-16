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
  defaultHeaders,
  assertUnauthorizedRequest,
  assertNotFoundRequest,
} from '../../../../utils/http';
import {deployResourceAndGetMetadata} from '../../../../utils/requestHelpers';
import {readFileSync} from 'node:fs';

function getExpectedContent(resourceName: string): string {
  const resourcePath = `./resources/${resourceName}`;
  return readFileSync(resourcePath, 'utf-8');
}

test.describe.parallel('Resource Get Content API', () => {
  test('Get Resource Content - RPA Success 200', async ({request}) => {
    const resourceName = 'rpa_get_resource_api_test.rpa';
    const metadata = await deployResourceAndGetMetadata(
      request,
      resourceName,
      0,
    );
    const expectedContent = getExpectedContent(resourceName);

    const res = await request.get(
      buildUrl('/resources/{resourceKey}/content', {
        resourceKey: metadata.resourceKey,
      }),
      {
        headers: defaultHeaders(),
      },
    );

    await assertStatusCode(res, 200);
    const content = await res.text();
    expect(content).toBe(expectedContent);
  });

  test('Get Resource Content - Not Found 404', async ({request}) => {
    const nonExistentResourceKey = '2251799813733053';

    const res = await request.get(
      buildUrl('/resources/{resourceKey}/content', {
        resourceKey: nonExistentResourceKey,
      }),
      {
        headers: defaultHeaders(),
      },
    );

    await assertNotFoundRequest(
      res,
      "Command 'FETCH' rejected with code 'NOT_FOUND': Expected to fetch resource but no resource found with key `2251799813733053`",
    );
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Resource Content - Unauthorized 401', async ({request}) => {
    const res = await request.get(
      buildUrl('/resources/{resourceKey}/content', {
        resourceKey: 'someKey',
      }),
      {
        headers: {},
      },
    );

    await assertUnauthorizedRequest(res);
  });
});
