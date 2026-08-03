/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';
import {CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT} from '@requestHelpers';

const MESSAGE_CORRELATION_ENDPOINT = '/messages/correlation';
const START_MESSAGE_NAME = 'correlated_subscription_biz_msg';
const MESSAGE_START_PROCESS_ID = 'correlated_subscription_business_id_process';

const runPrefix = `corr-sub-${generateUniqueId()}`;
const BUSINESS_ID_A = `${runPrefix}-aaa`;
const BUSINESS_ID_B = `${runPrefix}-zzz`;

const REQUIRED_FIELDS = [
  'businessId',
  'correlationKey',
  'correlationTime',
  'elementId',
  'messageKey',
  'messageName',
  'partitionId',
  'processDefinitionId',
  'processInstanceKey',
  'subscriptionKey',
  'tenantId',
];

async function correlateStartMessage(
  request: APIRequestContext,
  businessId: string,
) {
  const res = await request.post(buildUrl(MESSAGE_CORRELATION_ENDPOINT), {
    headers: jsonHeaders(),
    data: {name: START_MESSAGE_NAME, correlationKey: '', businessId},
  });
  await assertStatusCode(res, 200);
  return (await res.json()).processInstanceKey as string;
}

async function searchCorrelatedSubscriptions(
  request: APIRequestContext,
  filter: Record<string, unknown>,
  sort?: Record<string, unknown>[],
) {
  const res = await request.post(
    buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
    {
      headers: jsonHeaders(),
      data: {filter, ...(sort ? {sort} : {})},
    },
  );
  await assertStatusCode(res, 200);
  return res.json();
}

// Skipped due to bug #58207: https://github.com/camunda/camunda/issues/58207
// The setup correlates to a message start event with a businessId, which returns
// 404 NOT_FOUND on multi-partition clusters when the businessId hashes to a
// different partition than the correlate lands on (P_B != P_K). The cross-partition
// delegation creates the instance but the synchronous correlate reports NOT_FOUND,
// so beforeAll fails ~50% of the time on the multi-partition nightly. Re-enable
// once the engine reflects the delegated outcome in the correlate response.
test.describe
  .skip('Correlated Message Subscriptions - Business ID Search API', () => {
  const state: Record<string, string> = {
    processInstanceKeyA: '',
    processInstanceKeyB: '',
  };

  test.beforeAll(async ({request}) => {
    await deploy([
      './resources/correlated_subscription_business_id_process.bpmn',
    ]);

    state['processInstanceKeyA'] = await correlateStartMessage(
      request,
      BUSINESS_ID_A,
    );
    state['processInstanceKeyB'] = await correlateStartMessage(
      request,
      BUSINESS_ID_B,
    );

    await expect(async () => {
      const json = await searchCorrelatedSubscriptions(request, {
        businessId: {$like: `${runPrefix}-*`},
      });
      expect(json.page.totalItems).toBe(2);
    }).toPass(defaultAssertionOptions);
  });

  test.afterAll(async () => {
    if (state['processInstanceKeyA']) {
      await cancelProcessInstance(state['processInstanceKeyA']);
    }
    if (state['processInstanceKeyB']) {
      await cancelProcessInstance(state['processInstanceKeyB']);
    }
  });

  test('Correlated subscription exposes the Business ID of the message start correlation', async ({
    request,
  }) => {
    const json = await searchCorrelatedSubscriptions(request, {
      businessId: BUSINESS_ID_A,
    });
    expect(json.page.totalItems).toBe(1);
    const item = json.items[0];
    assertRequiredFields(item, REQUIRED_FIELDS);
    expect(item.businessId).toBe(BUSINESS_ID_A);
    expect(item.processDefinitionId).toBe(MESSAGE_START_PROCESS_ID);
    expect(item.processInstanceKey).toBe(state['processInstanceKeyA']);
  });

  test('Filter correlated subscriptions by Business ID with a like wildcard returns both', async ({
    request,
  }) => {
    const json = await searchCorrelatedSubscriptions(request, {
      businessId: {$like: `${runPrefix}-*`},
    });
    expect(json.page.totalItems).toBe(2);
    expect(
      json.items.map((i: {businessId: string}) => i.businessId).sort(),
    ).toEqual([BUSINESS_ID_A, BUSINESS_ID_B]);
  });

  test('Filter correlated subscriptions by a non-matching Business ID returns empty', async ({
    request,
  }) => {
    const json = await searchCorrelatedSubscriptions(request, {
      businessId: `${runPrefix}-none`,
    });
    expect(json.page.totalItems).toBe(0);
  });

  test('Sort correlated subscriptions by Business ID ascending and descending', async ({
    request,
  }) => {
    const filter = {businessId: {$like: `${runPrefix}-*`}};

    const ascending = await searchCorrelatedSubscriptions(request, filter, [
      {field: 'businessId', order: 'asc'},
    ]);
    expect(
      ascending.items.map((i: {businessId: string}) => i.businessId),
    ).toEqual([BUSINESS_ID_A, BUSINESS_ID_B]);

    const descending = await searchCorrelatedSubscriptions(request, filter, [
      {field: 'businessId', order: 'desc'},
    ]);
    expect(
      descending.items.map((i: {businessId: string}) => i.businessId),
    ).toEqual([BUSINESS_ID_B, BUSINESS_ID_A]);
  });
});
