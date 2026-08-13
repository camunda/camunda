/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertNotFoundRequest,
  assertStatusCode,
  assertUnauthorizedRequest,
  authHeaders,
  buildUrl,
  credentials,
} from '../../../../utils/http';
import {
  deployInlineResource,
  rpaResourceContent,
  uniqueResourceName,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const PNG_BYTES = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  'base64',
);

function contentBinaryUrl(resourceKey: string): string {
  return buildUrl('/resources/{resourceKey}/content/binary', {resourceKey});
}

// Workaround for bug #59831: https://github.com/camunda/camunda/issues/59831
// The handler is mapped with the gateway's default JSON produces list, so an
// explicit `Accept: application/octet-stream` — the media type the API spec
// documents — is answered with 406. Send no Accept header until that is fixed.
function binaryHeaders(): Record<string, string> {
  return authHeaders(credentials.accessToken);
}

test.describe.parallel('Resource Get Content Binary API', () => {
  test('Get Resource Content Binary - Generic Resource Success 200', async ({
    request,
  }) => {
    const content = '# System prompt\n\nYou are a helpful agent.';
    const resource = await deployInlineResource(
      request,
      uniqueResourceName('system-prompt', 'md'),
      content,
    );

    await expect(async () => {
      const res = await request.get(contentBinaryUrl(resource.resourceKey), {
        headers: binaryHeaders(),
      });

      await assertStatusCode(res, 200);
      expect(res.headers()['content-type']).toContain(
        'application/octet-stream',
      );
      expect(await res.text()).toBe(content);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Resource Content Binary - RPA Resource Success 200', async ({
    request,
  }) => {
    const content = rpaResourceContent(`rpa_${generateUniqueId()}`);
    const resource = await deployInlineResource(
      request,
      uniqueResourceName('robot', 'rpa'),
      content,
    );

    await expect(async () => {
      const res = await request.get(contentBinaryUrl(resource.resourceKey), {
        headers: binaryHeaders(),
      });

      await assertStatusCode(res, 200);
      expect(await res.text()).toBe(content);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Resource Content Binary - returns byte-identical binary content', async ({
    request,
  }) => {
    const resource = await deployInlineResource(
      request,
      uniqueResourceName('logo', 'png'),
      PNG_BYTES,
    );

    await expect(async () => {
      const res = await request.get(contentBinaryUrl(resource.resourceKey), {
        headers: binaryHeaders(),
      });

      await assertStatusCode(res, 200);
      expect(await res.body()).toEqual(PNG_BYTES);
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Resource Content Binary - Not Found 404', async ({request}) => {
    const nonExistentResourceKey = '2251799813733053';

    const res = await request.get(contentBinaryUrl(nonExistentResourceKey), {
      headers: binaryHeaders(),
    });

    await assertNotFoundRequest(
      res,
      `Resource with key '${nonExistentResourceKey}' not found`,
    );
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Resource Content Binary - Unauthorized 401', async ({request}) => {
    const res = await request.get(contentBinaryUrl('someKey'), {
      headers: {},
    });

    await assertUnauthorizedRequest(res);
  });
});
