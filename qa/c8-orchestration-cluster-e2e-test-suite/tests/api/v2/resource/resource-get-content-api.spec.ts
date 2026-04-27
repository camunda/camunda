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
import {getExpectedContent} from '../../../../utils/beans/requestBeans';
import {deployResourceAndGetMetadata} from '@requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';

test.describe.parallel('Resource Get Content API', () => {
  test('Get Resource Content - RPA Success 200', async ({request}) => {
    const resourceName = 'rpa_get_resource_api_test.rpa';
    const metadata = await deployResourceAndGetMetadata(
      request,
      resourceName,
      0,
    );
    const expectedContent = getExpectedContent(resourceName);

    await expect(async () => {
      const res = await request.get(
        buildUrl('/resources/{resourceKey}/content', {
          resourceKey: metadata.resourceKey,
        }),
        {
          headers: defaultHeaders(),
        },
      );

      await assertStatusCode(res, 200);
      expect(await res.text()).toBe(expectedContent);
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
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
      `Resource with key '${nonExistentResourceKey}' not found`,
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
