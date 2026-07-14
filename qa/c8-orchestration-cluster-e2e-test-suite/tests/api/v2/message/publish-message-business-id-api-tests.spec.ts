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
  assertStatusCode,
  buildUrl,
  jsonHeaders,
} from '../../../../utils/http';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {
  defaultAssertionOptions,
  generateUniqueId,
  uniqueBusinessId,
} from '../../../../utils/constants';

const MESSAGE_PUBLICATION_ENDPOINT = '/messages/publication';
const PROCESS_INSTANCE_SEARCH_ENDPOINT = '/process-instances/search';
const START_MESSAGE_NAME = 'start_business_id_msg';
const MESSAGE_START_PROCESS_ID = 'message_start_business_id_process';

const uniqueMessageId = (): string => `msg-${generateUniqueId()}`;

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

/* eslint-disable playwright/expect-expect */
test.describe.parallel('Publish Message - Business ID API', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/message_start_business_id_process.bpmn']);
  });

  test('Publish message to a message start event carries the Business ID to the created instance', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('publish-start');
    const localState: Record<string, string> = {processInstanceKey: ''};

    await test.step('Publish message with a Business ID', async () => {
      const res = await request.post(buildUrl(MESSAGE_PUBLICATION_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          name: START_MESSAGE_NAME,
          correlationKey: '',
          messageId: uniqueMessageId(),
          businessId,
        },
      });
      await assertStatusCode(res, 200);
      const json = await res.json();
      expect(json.messageKey).toBeDefined();
    });

    await test.step('Started instance is searchable by the Business ID', async () => {
      await expect(async () => {
        const json = await searchInstancesByBusinessId(request, businessId);
        expect(json.page.totalItems).toBe(1);
        expect(json.items[0].businessId).toBe(businessId);
        expect(json.items[0].processDefinitionId).toBe(
          MESSAGE_START_PROCESS_ID,
        );
        localState['processInstanceKey'] = json.items[0].processInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey']);
  });

  test('Publish message without a Business ID starts an instance with a null Business ID', async ({
    request,
  }) => {
    const marker = generateUniqueId();
    const localState: Record<string, string> = {processInstanceKey: ''};

    await test.step('Publish message without a Business ID', async () => {
      const res = await request.post(buildUrl(MESSAGE_PUBLICATION_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          name: START_MESSAGE_NAME,
          correlationKey: '',
          messageId: uniqueMessageId(),
          variables: {marker},
        },
      });
      await assertStatusCode(res, 200);
    });

    await test.step('Started instance has a null Business ID', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(PROCESS_INSTANCE_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processDefinitionId: MESSAGE_START_PROCESS_ID,
                variables: [{name: 'marker', value: `"${marker}"`}],
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.page.totalItems).toBe(1);
        expect(json.items[0]).toHaveProperty('businessId', null);
        localState['processInstanceKey'] = json.items[0].processInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await cancelProcessInstance(localState['processInstanceKey']);
  });

  test('Duplicate Business ID publish does not start a second instance while the first is active', async ({
    request,
  }) => {
    const businessId = uniqueBusinessId('publish-duplicate');
    const localState: Record<string, string> = {processInstanceKey: ''};

    await test.step('Publish first message with the Business ID', async () => {
      const res = await request.post(buildUrl(MESSAGE_PUBLICATION_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          name: START_MESSAGE_NAME,
          correlationKey: '',
          messageId: uniqueMessageId(),
          businessId,
        },
      });
      await assertStatusCode(res, 200);
    });

    await test.step('First instance becomes visible for the Business ID', async () => {
      await expect(async () => {
        const json = await searchInstancesByBusinessId(request, businessId);
        expect(json.page.totalItems).toBe(1);
        localState['processInstanceKey'] = json.items[0].processInstanceKey;
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Publish second message with the same Business ID (different message id)', async () => {
      const res = await request.post(buildUrl(MESSAGE_PUBLICATION_ENDPOINT), {
        headers: jsonHeaders(),
        data: {
          name: START_MESSAGE_NAME,
          correlationKey: '',
          messageId: uniqueMessageId(),
          businessId,
        },
      });
      await assertStatusCode(res, 200);
    });

    await test.step('Still exactly one instance exists for the Business ID', async () => {
      // The duplicate publish is suppressed by Business ID uniqueness, so the count must stay at 1.
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

  test('Publish message with a Business ID exceeding the maximum length is rejected', async ({
    request,
  }) => {
    const res = await request.post(buildUrl(MESSAGE_PUBLICATION_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        name: START_MESSAGE_NAME,
        correlationKey: '',
        messageId: uniqueMessageId(),
        businessId: 'a'.repeat(257),
      },
    });
    await assertBadRequest(res, /businessId|256/i, 'INVALID_ARGUMENT');
  });
});
