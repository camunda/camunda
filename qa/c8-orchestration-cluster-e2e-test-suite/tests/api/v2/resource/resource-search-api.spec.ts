/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  assertBadRequest,
  assertPaginatedRequest,
  assertUnauthorizedRequest,
  assertStatusCode,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {
  assertResourceInSearchResult,
  DeployedResource,
  deployInlineResource,
  deployInlineResources,
  deployResourceAndGetMetadata,
  resourceKeysOf,
  RESOURCE_SEARCH_ENDPOINT,
  rpaResourceContent,
  searchResources,
  uniqueResourceName,
  ResourceMetadata,
} from '@requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const SORT_FIELDS = [
  'resourceKey',
  'resourceName',
  'resourceId',
  'version',
  'versionTag',
  'deploymentKey',
  'tenantId',
] as const;

async function searchAndValidate(
  request: Parameters<typeof searchResources>[0],
  body: Record<string, unknown>,
) {
  const res = await searchResources(request, body);
  await validateResponse(
    {path: RESOURCE_SEARCH_ENDPOINT, method: 'POST', status: '200'},
    res,
  );
  return res;
}

test.describe('Resource Search API', () => {
  let deploymentKey: string;
  let genericResources: DeployedResource[];
  let rpaResource: DeployedResource;
  let versionTag: string;
  let versionedV1: DeployedResource;
  let versionedV2: DeployedResource;
  let processDefinition: ResourceMetadata;

  test.beforeAll(async ({request}) => {
    const suffix = generateUniqueId();
    const deployment = await deployInlineResources(request, [
      {fileName: `prompt-${suffix}.md`, content: '# System prompt'},
      {fileName: `config-${suffix}.yml`, content: 'server:\n  port: 8080'},
      {fileName: `notes-${suffix}.txt`, content: 'plain text resource'},
    ]);
    deploymentKey = deployment.deploymentKey;
    genericResources = deployment.resources;

    versionTag = `vt-${suffix}`;
    rpaResource = await deployInlineResource(
      request,
      `robot-${suffix}.rpa`,
      rpaResourceContent(`rpa_${suffix}`, versionTag),
    );

    const versionedName = uniqueResourceName('versioned', 'md');
    versionedV1 = await deployInlineResource(
      request,
      versionedName,
      '# version one',
    );
    versionedV2 = await deployInlineResource(
      request,
      versionedName,
      '# version two',
    );

    processDefinition = await deployResourceAndGetMetadata(
      request,
      'Zeebe_User_Task_Process.bpmn',
      0,
    );
  });

  test('Search Resources - returns deployed generic and RPA resources', async ({
    request,
  }) => {
    await expect(async () => {
      const unfiltered = await searchAndValidate(request, {});
      await assertPaginatedRequest(unfiltered, {
        itemLengthGreaterThan: 0,
        totalItemGreaterThan: 0,
      });

      const res = await searchAndValidate(request, {
        filter: {
          resourceKey: {
            $in: [
              ...genericResources.map((resource) => resource.resourceKey),
              rpaResource.resourceKey,
            ],
          },
        },
      });
      const json = await res.json();
      for (const resource of genericResources) {
        assertResourceInSearchResult(json, resource);
      }
      assertResourceInSearchResult(json, rpaResource, versionTag);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - excludes process definitions', async ({request}) => {
    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {resourceKey: processDefinition.resourceKey},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 0,
        totalItemsEqualTo: 0,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - by resourceKey', async ({request}) => {
    const expected = genericResources[0];

    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {resourceKey: expected.resourceKey},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      assertResourceInSearchResult(await res.json(), expected);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - by resourceId', async ({request}) => {
    const expected = genericResources[1];

    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {resourceId: expected.resourceId},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      assertResourceInSearchResult(await res.json(), expected);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - by resourceName', async ({request}) => {
    const expected = genericResources[2];

    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {resourceName: expected.resourceName},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      assertResourceInSearchResult(await res.json(), expected);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - by deploymentKey', async ({request}) => {
    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {deploymentKey},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: genericResources.length,
        totalItemsEqualTo: genericResources.length,
      });
      const json = await res.json();
      for (const resource of genericResources) {
        assertResourceInSearchResult(json, resource);
      }
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - by tenantId', async ({request}) => {
    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {tenantId: '<default>', deploymentKey},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: genericResources.length,
        totalItemsEqualTo: genericResources.length,
      });
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - by versionTag', async ({request}) => {
    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {versionTag},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      assertResourceInSearchResult(await res.json(), rpaResource, versionTag);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - by version', async ({request}) => {
    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {resourceId: versionedV2.resourceId, version: {$gt: 1}},
      });

      await assertPaginatedRequest(res, {
        itemsLengthEqualTo: 1,
        totalItemsEqualTo: 1,
      });
      assertResourceInSearchResult(await res.json(), versionedV2);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Resources - advanced filter operators', async ({request}) => {
    const [first, second, third] = genericResources;

    await expect(async () => {
      const inRes = await searchAndValidate(request, {
        filter: {
          resourceKey: {$in: [first.resourceKey, second.resourceKey]},
        },
      });
      expect(resourceKeysOf(await inRes.json()).sort()).toEqual(
        [first.resourceKey, second.resourceKey].sort(),
      );

      const notInRes = await searchAndValidate(request, {
        filter: {
          deploymentKey,
          resourceKey: {$notIn: [first.resourceKey]},
        },
      });
      expect(resourceKeysOf(await notInRes.json()).sort()).toEqual(
        [second.resourceKey, third.resourceKey].sort(),
      );

      const neqRes = await searchAndValidate(request, {
        filter: {deploymentKey, resourceId: {$neq: first.resourceId}},
      });
      expect(resourceKeysOf(await neqRes.json())).not.toContain(
        first.resourceKey,
      );

      const eqRes = await searchAndValidate(request, {
        filter: {resourceName: {$eq: first.resourceName}},
      });
      expect(resourceKeysOf(await eqRes.json())).toEqual([first.resourceKey]);

      const existsRes = await searchAndValidate(request, {
        filter: {deploymentKey, resourceId: {$exists: true}},
      });
      expect(resourceKeysOf(await existsRes.json()).sort()).toEqual(
        genericResources.map((resource) => resource.resourceKey).sort(),
      );
    }).toPass(defaultAssertionOptions);
  });

  for (const field of ['resourceKey', 'resourceName', 'resourceId'] as const) {
    test(`Search Resources - sort by ${field} ascending and descending`, async ({
      request,
    }) => {
      await expect(async () => {
        const ascRes = await searchAndValidate(request, {
          filter: {deploymentKey},
          sort: [{field, order: 'ASC'}],
        });
        const ascending = (await ascRes.json()).items.map(
          (item: Record<string, string>) => item[field],
        );
        expect(ascending).toHaveLength(genericResources.length);
        expect(ascending).toEqual([...ascending].sort());

        const descRes = await searchAndValidate(request, {
          filter: {deploymentKey},
          sort: [{field, order: 'DESC'}],
        });
        const descending = (await descRes.json()).items.map(
          (item: Record<string, string>) => item[field],
        );
        expect(descending).toEqual([...ascending].reverse());
      }).toPass(defaultAssertionOptions);
    });
  }

  test('Search Resources - sort by version', async ({request}) => {
    await expect(async () => {
      const res = await searchAndValidate(request, {
        filter: {resourceId: versionedV1.resourceId},
        sort: [{field: 'version', order: 'DESC'}],
      });

      const versions = (await res.json()).items.map(
        (item: Record<string, number>) => item.version,
      );
      expect(versions).toEqual([versionedV2.version, versionedV1.version]);
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Search Resources - accepts every documented sort field', async ({
    request,
  }) => {
    for (const field of SORT_FIELDS) {
      const res = await searchResources(request, {
        sort: [{field, order: 'ASC'}],
        page: {limit: 1},
      });
      await assertStatusCode(res, 200);
    }
  });

  test('Search Resources - paginates with a cursor', async ({request}) => {
    await expect(async () => {
      const firstRes = await searchAndValidate(request, {
        filter: {deploymentKey},
        sort: [{field: 'resourceKey', order: 'ASC'}],
        page: {limit: 2},
      });
      const firstPage = await firstRes.json();
      expect(firstPage.items).toHaveLength(2);
      expect(firstPage.page.totalItems).toBe(genericResources.length);

      const secondRes = await searchAndValidate(request, {
        filter: {deploymentKey},
        sort: [{field: 'resourceKey', order: 'ASC'}],
        page: {limit: 2, after: firstPage.page.endCursor},
      });
      const secondPage = await secondRes.json();
      expect(secondPage.items).toHaveLength(1);

      const keys = [
        ...resourceKeysOf(firstPage),
        ...resourceKeysOf(secondPage),
      ];
      expect(new Set(keys).size).toBe(genericResources.length);
      expect(keys.sort()).toEqual(
        genericResources.map((resource) => resource.resourceKey).sort(),
      );
    }).toPass(defaultAssertionOptions);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Search Resources - Bad Request 400 - unknown sort field', async ({
    request,
  }) => {
    const res = await searchResources(request, {
      sort: [{field: 'unknownField', order: 'ASC'}],
    });

    await assertBadRequest(res, /unknownField/);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Search Resources - Bad Request 400 - unknown sort order', async ({
    request,
  }) => {
    const res = await searchResources(request, {
      sort: [{field: 'resourceKey', order: 'SIDEWAYS'}],
    });

    await assertBadRequest(res, /SIDEWAYS/);
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Search Resources - Unauthorized 401', async ({request}) => {
    const res = await searchResources(
      request,
      {},
      {headers: {'Content-Type': 'application/json'}},
    );

    await assertUnauthorizedRequest(res);
  });
});
