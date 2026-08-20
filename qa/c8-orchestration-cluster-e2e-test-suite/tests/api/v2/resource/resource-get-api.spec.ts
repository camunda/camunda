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
import {JSONDoc} from '@camunda8/sdk/dist/zeebe/types.js';
import {
  validateResponse,
  validateResponseShape,
} from '../../../../json-body-assertions';
import {
  deployInlineResource,
  deployResourceAndGetMetadata,
  getResource,
  RESOURCE_ENDPOINT,
  ResourceMetadata,
  uniqueResourceName,
} from '@requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';

function validateResourceResponse(
  body: JSONDoc,
  expectedMetadata: ResourceMetadata,
): void {
  validateResponseShape(
    {
      path: RESOURCE_ENDPOINT,
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
  test('Get Resource - RPA Success 200', async ({request}) => {
    const resourceName = 'rpa_get_resource_api_test.rpa';
    const metadata = await deployResourceAndGetMetadata(
      request,
      resourceName,
      0,
    );

    await expect(async () => {
      const res = await getResource(request, metadata.resourceKey);

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: RESOURCE_ENDPOINT,
          method: 'GET',
          status: '200',
        },
        res,
      );
      validateResourceResponse(await res.json(), metadata);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Resource - Generic Resource Success 200', async ({request}) => {
    const resourceName = uniqueResourceName('system-prompt', 'md');
    const metadata = await deployInlineResource(
      request,
      resourceName,
      '# System prompt',
    );

    await expect(async () => {
      const res = await getResource(request, metadata.resourceKey);

      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: RESOURCE_ENDPOINT,
          method: 'GET',
          status: '200',
        },
        res,
      );
      validateResourceResponse(await res.json(), metadata);
    }).toPass(defaultAssertionOptions);
  });

  test('Get Resource - Not Found 404 For A Process Definition Key', async ({
    request,
  }) => {
    const processDefinition = await deployResourceAndGetMetadata(
      request,
      'Zeebe_User_Task_Process.bpmn',
      0,
    );

    // Both endpoints read secondary storage. Waiting for the process definition
    // to be projected is what makes the 404 below meaningful — asserted earlier
    // it would pass simply because nothing has been indexed yet.
    await expect(async () => {
      const definitionRes = await request.get(
        buildUrl('/process-definitions/{processDefinitionKey}', {
          processDefinitionKey: processDefinition.resourceKey,
        }),
        {headers: jsonHeaders()},
      );
      await assertStatusCode(definitionRes, 200);
    }).toPass(defaultAssertionOptions);

    const res = await getResource(request, processDefinition.resourceKey);

    await assertNotFoundRequest(
      res,
      `Resource with key '${processDefinition.resourceKey}' not found`,
    );
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Resource - Not Found 404', async ({request}) => {
    const nonExistentResourceKey = '2251799813733053';

    const res = await getResource(request, nonExistentResourceKey);

    await assertNotFoundRequest(
      res,
      `Resource with key '${nonExistentResourceKey}' not found`,
    );
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Get Resource - Unauthorized 401', async ({request}) => {
    const res = await getResource(request, 'someKey', {headers: {}});

    await assertUnauthorizedRequest(res);
  });
});
