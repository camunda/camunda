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
  assertUnauthorizedRequest,
  assertNotFoundRequest,
  assertBadRequest,
  assertPaginatedRequest,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  deleteResource,
  deployInlineResource,
  deployResourceAndGetMetadata,
  getResource,
  getResourceContentBinary,
  RESOURCE_DELETION_ENDPOINT,
  searchResources,
  uniqueResourceName,
} from '@requestHelpers';
import {defaultAssertionOptions} from '../../../../utils/constants';

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

    const res = await deleteResource(request, resourceKey);

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: RESOURCE_DELETION_ENDPOINT,
        method: 'POST',
        status: '200',
      },
      res,
    );
  });

  test('Delete Resource - deleted generic resource stops being served', async ({
    request,
  }) => {
    const resource = await deployInlineResource(
      request,
      uniqueResourceName('system-prompt', 'md'),
      '# System prompt',
    );
    const resourceKey = resource.resourceKey;

    await expect(async () => {
      const searchRes = await searchResources(request, {filter: {resourceKey}});
      await assertPaginatedRequest(searchRes, {itemsLengthEqualTo: 1});

      const contentRes = await getResourceContentBinary(request, resourceKey);
      await assertStatusCode(contentRes, 200);
    }).toPass(defaultAssertionOptions);

    const deleteRes = await deleteResource(request, resourceKey);
    await assertStatusCode(deleteRes, 200);

    await expect(async () => {
      const searchRes = await searchResources(request, {filter: {resourceKey}});
      await assertPaginatedRequest(searchRes, {itemsLengthEqualTo: 0});

      const getRes = await getResource(request, resourceKey);
      await assertNotFoundRequest(
        getRes,
        `Resource with key '${resourceKey}' not found`,
      );

      const contentRes = await getResourceContentBinary(request, resourceKey);
      await assertNotFoundRequest(
        contentRes,
        `Resource with key '${resourceKey}' not found`,
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Delete Resource - Not Found 404', async ({request}) => {
    const nonExistentResourceKey = '2251799813733053';

    const res = await deleteResource(request, nonExistentResourceKey);

    await assertNotFoundRequest(
      res,
      "Command 'DELETE' rejected with code 'NOT_FOUND': Expected to delete resource but no resource found with key `2251799813733053`",
    );
  });

  test('Delete Resource - Bad Request 400 - Invalid resourceKey Format', async ({
    request,
  }) => {
    const invalidResourceKey = 'invalid-string-key';

    const res = await deleteResource(request, invalidResourceKey);

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

    const res = await deleteResource(request, resourceKey, {
      data: {operationReference: 'invalid-string-reference'},
    });

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

    const res = await deleteResource(request, resourceKey, {headers: {}});

    await assertUnauthorizedRequest(res);
  });
});
