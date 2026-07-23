/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../../../../utils/http';
import {deploy} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const PROCESS_INSTANCE_ENDPOINT = '/process-instances';
const DECISION_INSTANCE_SEARCH_ENDPOINT = '/decision-instances/search';
const DMN_PROCESS_ID = 'mammalAnimalProcess';

const runPrefix = `dec-${generateUniqueId()}`;
const BUSINESS_ID_A = `${runPrefix}-aaa`;
const BUSINESS_ID_B = `${runPrefix}-zzz`;

async function startDmnProcessWithBusinessId(
  request: APIRequestContext,
  businessId: string,
) {
  const res = await request.post(buildUrl(PROCESS_INSTANCE_ENDPOINT), {
    headers: jsonHeaders(),
    data: {
      processDefinitionId: DMN_PROCESS_ID,
      businessId,
      variables: {hasHairOrFur: true, warmBlooded: true, givesMilk: true},
    },
  });
  await assertStatusCode(res, 200);
  return (await res.json()).processInstanceKey as string;
}

async function searchDecisionInstances(
  request: APIRequestContext,
  filter: Record<string, unknown>,
  sort?: Record<string, unknown>[],
) {
  const res = await request.post(buildUrl(DECISION_INSTANCE_SEARCH_ENDPOINT), {
    headers: jsonHeaders(),
    data: {filter, ...(sort ? {sort} : {})},
  });
  await assertStatusCode(res, 200);
  return res.json();
}

test.describe.serial('Search Decision Instances - Business ID API', () => {
  test.beforeAll(async ({request}) => {
    await deploy([
      './resources/mammalAnimalProcess.bpmn',
      './resources/isMammal_.dmn',
    ]);

    await startDmnProcessWithBusinessId(request, BUSINESS_ID_A);
    await startDmnProcessWithBusinessId(request, BUSINESS_ID_B);

    await expect(async () => {
      const json = await searchDecisionInstances(request, {
        businessId: {$like: `${runPrefix}-*`},
      });
      expect(json.page.totalItems).toBe(2);
    }).toPass(defaultAssertionOptions);
  });

  test('Decision instance exposes and is filterable by the owning instance Business ID', async ({
    request,
  }) => {
    const json = await searchDecisionInstances(request, {
      businessId: BUSINESS_ID_A,
    });
    expect(json.page.totalItems).toBe(1);
    expect(json.items[0].businessId).toBe(BUSINESS_ID_A);
  });

  test('Filter decision instances by Business ID with a like wildcard returns both', async ({
    request,
  }) => {
    const json = await searchDecisionInstances(request, {
      businessId: {$like: `${runPrefix}-*`},
    });
    expect(json.page.totalItems).toBe(2);
    expect(
      json.items.map((i: {businessId: string}) => i.businessId).sort(),
    ).toEqual([BUSINESS_ID_A, BUSINESS_ID_B]);
  });

  test('Filter decision instances by a non-matching Business ID returns empty', async ({
    request,
  }) => {
    const json = await searchDecisionInstances(request, {
      businessId: `${runPrefix}-none`,
    });
    expect(json.page.totalItems).toBe(0);
  });

  test('Sort decision instances by Business ID ascending and descending', async ({
    request,
  }) => {
    const filter = {businessId: {$like: `${runPrefix}-*`}};

    const ascending = await searchDecisionInstances(request, filter, [
      {field: 'businessId', order: 'asc'},
    ]);
    expect(
      ascending.items.map((i: {businessId: string}) => i.businessId),
    ).toEqual([BUSINESS_ID_A, BUSINESS_ID_B]);

    const descending = await searchDecisionInstances(request, filter, [
      {field: 'businessId', order: 'desc'},
    ]);
    expect(
      descending.items.map((i: {businessId: string}) => i.businessId),
    ).toEqual([BUSINESS_ID_B, BUSINESS_ID_A]);
  });
});
