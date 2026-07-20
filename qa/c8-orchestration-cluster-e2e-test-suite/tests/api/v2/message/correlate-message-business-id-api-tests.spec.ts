/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, expect, test} from '@playwright/test';
import {
  assertBadRequest,
  assertNotFoundRequest,
  assertRequiredFields,
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  extendedAssertionOptions,
  uniqueBusinessId,
} from '../../../../utils/constants';
import {correlateMessageRequiredFields} from '../../../../utils/beans/requestBeans';

const MESSAGE_CORRELATION_ENDPOINT = '/messages/correlation';
const PROCESS_INSTANCE_SEARCH_ENDPOINT = '/process-instances/search';
const START_MESSAGE_NAME = 'start_business_id_msg';
const MESSAGE_START_PROCESS_ID = 'message_start_business_id_process';

// Searches for the process instances started for the message-start process that carry the given
// Business ID. Business IDs are unique per test, so the result is scoped to the current test.
async function searchInstancesByBusinessId(
  request: APIRequestContext,
  businessId: string,
) {
  const res = await request.post(buildUrl(PROCESS_INSTANCE_SEARCH_ENDPOINT), {
    headers: jsonHeaders(),
    data: {filter: {businessId}},
  });
  await assertStatusCode(res, 200);
  return res.json();
}

async function correlateStartMessage(
  request: APIRequestContext,
  businessId?: string,
) {
  return request.post(buildUrl(MESSAGE_CORRELATION_ENDPOINT), {
    headers: jsonHeaders(),
    data: {
      name: START_MESSAGE_NAME,
      correlationKey: '',
      ...(businessId !== undefined ? {businessId} : {}),
    },
  });
}

// The message-start subscription opens synchronously on the deployment's origin partition but is
// distributed to the other partitions asynchronously. A correlate carries no TTL (unlike a
// buffered publish), so it is rejected NOT_FOUND ("but none was found") whenever it reaches a
// partition where the subscription is not yet live. Under heavy parallel load this distribution
// lag can exceed the default 30s window, so poll with the extended (loaded-cluster) window until
// the subscription is live. A rejected correlate creates no instance and the loop stops at the
// first success, so exactly one instance results.
async function correlateStartMessageUntilCreated(
  request: APIRequestContext,
  businessId: string,
): Promise<Record<string, string>> {
  let created: Record<string, string> = {};
  await expect(async () => {
    const res = await correlateStartMessage(request, businessId);
    await assertStatusCode(res, 200);
    created = await res.json();
  }).toPass(extendedAssertionOptions);
  return created;
}

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Correlate Message - Business ID API', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/message_start_business_id_process.bpmn']);
  });

  test('Correlate message to a message start event carries the Business ID to the created instance', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('correlate-start');
    const localState: Record<string, string> = {processInstanceKey: ''};

    await test.step('Correlate message with a Business ID', async () => {
      const json = await correlateStartMessageUntilCreated(request, businessId);
      assertRequiredFields(json, correlateMessageRequiredFields);
      expect(json.processInstanceKey).toBeDefined();
      localState['processInstanceKey'] = json.processInstanceKey;
    });

    await test.step('Created instance is searchable by the Business ID', async () => {
      await expect(async () => {
        const json = await searchInstancesByBusinessId(request, businessId);
        expect(json.page.totalItems).toBe(1);
        expect(json.items[0].businessId).toBe(businessId);
        expect(json.items[0].processInstanceKey).toBe(
          localState['processInstanceKey'],
        );
        expect(json.items[0].processDefinitionId).toBe(
          MESSAGE_START_PROCESS_ID,
        );
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey']);
  });

  test('Duplicate Business ID correlation does not start a second instance while the first is active', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('correlate-duplicate');
    const localState: Record<string, string> = {processInstanceKey: ''};

    await test.step('Correlate first message with the Business ID', async () => {
      const json = await correlateStartMessageUntilCreated(request, businessId);
      localState['processInstanceKey'] = json.processInstanceKey;
    });

    await test.step('First instance becomes visible for the Business ID', async () => {
      await expect(async () => {
        const json = await searchInstancesByBusinessId(request, businessId);
        expect(json.page.totalItems).toBe(1);
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Correlate second message with the same Business ID', async () => {
      const res = await correlateStartMessage(request, businessId);
      // The message start event is blocked by the active instance already holding this Business ID,
      // so there is no subscription to correlate to and the correlate command is rejected NOT_FOUND
      // (only one active instance per Business ID is allowed for message start events). Unlike a
      // buffered publish, a correlate carries no TTL, so the suppressed message is not retained.
      await assertNotFoundRequest(res, 'already active');
    });

    await test.step('Still exactly one instance exists for the Business ID', async () => {
      await expect(async () => {
        const json = await searchInstancesByBusinessId(request, businessId);
        expect(json.page.totalItems).toBe(1);
        expect(json.items[0].processInstanceKey).toBe(
          localState['processInstanceKey'],
        );
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey']);
  });

  test('Business ID can be reused for correlation after the holding instance is cancelled', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('correlate-reuse');
    const localState: Record<string, string> = {
      firstKey: '',
      secondKey: '',
    };

    await test.step('Correlate message and cancel the created instance', async () => {
      const json = await correlateStartMessageUntilCreated(request, businessId);
      localState['firstKey'] = json.processInstanceKey;
      await expect(async () => {
        const json = await searchInstancesByBusinessId(request, businessId);
        expect(json.page.totalItems).toBe(1);
      }).toPass(defaultAssertionOptions);
      await cancelProcessInstance(localState['firstKey']);
    });

    await test.step('Correlate again with the same Business ID after cancellation', async () => {
      // Wait until the cancelled holder is no longer ACTIVE so the Business ID lock is released,
      // then correlate. The retry only re-fires on a NOT_FOUND rejection (no instance created), so
      // it cannot leak instances and stops at the first successful correlate.
      await expect(async () => {
        const res = await request.post(
          buildUrl(PROCESS_INSTANCE_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(),
            data: {filter: {businessId, state: 'ACTIVE'}},
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);

      const json = await correlateStartMessageUntilCreated(request, businessId);
      expect(json.processInstanceKey).not.toBe(localState['firstKey']);
      localState['secondKey'] = json.processInstanceKey;
    });

    await cancelProcessInstance(localState['secondKey']);
  });

  test('Correlate message with a Business ID exceeding the maximum length is rejected', async ({
    request,
  }) => {
    const res = await correlateStartMessage(request, 'a'.repeat(257));
    await assertBadRequest(res, /businessId|256/i, 'INVALID_ARGUMENT');
  });
});
