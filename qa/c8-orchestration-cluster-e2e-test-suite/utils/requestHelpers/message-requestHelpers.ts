/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {expect} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';

export interface CorrelatedMessageSubscription {
  correlationKey: string;
  correlationTime: string;
  elementId: string;
  elementInstanceKey: string;
  messageKey: string;
  messageName: string;
  partitionId: number;
  processDefinitionId: string;
  processInstanceKey: string;
  subscriptionKey: string;
  tenantId: string;
}

export const CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT =
  '/correlated-message-subscriptions/search';

export async function searchCorrelatedMessageSubscriptions(
  request: APIRequestContext,
  messageKey: string,
) {
  await expect(async () => {
    const res = await request.post(
      buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            messageKey: messageKey,
          },
        },
      },
    );
    await assertStatusCode(res, 200);
    const json = await res.json();
    expect(json.page.totalItems).toEqual(1);
    expect(json.items.length).toEqual(1);
    const item = json.items[0];
    expect(item.messageKey).toEqual(messageKey);
  }).toPass({
    intervals: [5_000, 10_000, 15_000, 25_000, 35_000],
    timeout: 180_000,
  });
}
