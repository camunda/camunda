/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect, APIRequestContext} from '@playwright/test';
import {
  buildUrl,
  jsonHeaders,
  assertRequiredFields,
  assertEqualsForKeys,
  paginatedResponseFields,
  assertStatusCode,
  assertBadRequest,
  assertUnauthorizedRequest,
  encode,
} from '../../../../utils/http';
import {
  CORRELATE_MESSAGE,
  CORRELATE_MESSAGE1,
  CORRELATE_MESSAGE2,
  CORRELATE_MESSAGE_DOUBLE_1,
  CORRELATE_MESSAGE_DOUBLE_2,
  correlateMessageRequiredFields,
  correlatedMessageSubscriptionRequiredFields,
} from '../../../../utils/beans/requestBeans';
import {createInstances, deploy} from '../../../../utils/zeebeClient';
import {defaultAssertionOptions} from '../../../../utils/constants';
import {validateResponse} from 'json-body-assertions';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  expectProcessInstanceCanBeFound,
  type CorrelatedMessageSubscription,
  searchCorrelatedMessageSubscriptions,
  CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT,
  grantUserResourceAuthorization,
  createUser,
} from '@requestHelpers';

test.describe.serial('Correlated Message Subscriptions API Tests', () => {
  const state: Record<string, string> = {};
  const expectedCorrelationSubscriptionSearchResponse1 = {
    correlationKey: '3838383',
    elementId: 'Event_17u9bac',
    messageName: 'Message_3tvi9o8',
    partitionId: 1,
    processDefinitionId: 'messageCatchEvent3',
    tenantId: '<default>',
  };

  let messageKeys: string[] = [];
  const cleanups: ((request: APIRequestContext) => Promise<void>)[] = [];

  test.beforeAll(async ({request}) => {
    await test.step('Deploy processes', async () => {
      await deploy([
        './resources/messageCatchEvent3.bpmn',
        './resources/messageCatchEvent1.bpmn',
        './resources/messageCatchEvent2.bpmn',
        './resources/doubleMessageCatchEvent.bpmn',
      ]);
    });

    await test.step('Create process instances', async () => {
      const [procA] = await createInstances('messageCatchEvent3', 1, 1);
      const [procB] = await createInstances('messageCatchEvent1', 1, 1);
      const [procC] = await createInstances('messageCatchEvent2', 1, 1);
      const [procD] = await createInstances(
        'Process_double_message_catch',
        1,
        1,
      );
      state.processInstance1 = procA.processInstanceKey;
      state.processInstance2 = procB.processInstanceKey;
      state.processInstance3 = procC.processInstanceKey;
      state.processInstance4 = procD.processInstanceKey;
    });

    await test.step('Poll process instances', async () => {
      await expectProcessInstanceCanBeFound(request, state.processInstance1);
      await expectProcessInstanceCanBeFound(request, state.processInstance2);
      await expectProcessInstanceCanBeFound(request, state.processInstance3);
      await expectProcessInstanceCanBeFound(request, state.processInstance4);
    });

    await test.step('Correlate messages', async () => {
      const correlationPayloads = [
        {body: CORRELATE_MESSAGE, stateKey: 'messageKeyPrimary'},
        {body: CORRELATE_MESSAGE2, stateKey: 'messageKeySecondary'},
        {body: CORRELATE_MESSAGE1, stateKey: 'messageKeyTertiary'},
        {body: CORRELATE_MESSAGE_DOUBLE_1, stateKey: 'messageKeyDouble1'},
        {body: CORRELATE_MESSAGE_DOUBLE_2, stateKey: 'messageKeyDouble2'},
      ];

      for (const payload of correlationPayloads) {
        const res = await request.post(buildUrl('/messages/correlation'), {
          headers: jsonHeaders(),
          data: payload.body,
        });
        expect(res.status()).toBe(200);
        await validateResponse(
          {
            path: '/messages/correlation',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        assertRequiredFields(json, correlateMessageRequiredFields);
        state[payload.stateKey] = json.messageKey;
        messageKeys.push(json.messageKey);
      }
    });

    await test.step('Wait for message subscriptions to be created', async () => {
      for (const key of messageKeys) {
        await searchCorrelatedMessageSubscriptions(request, key);
      }
    });
  });

  test.afterAll(async ({request}) => {
    for (const key of Object.keys(state)) {
      delete state[key];
    }
    for (const cleanup of cleanups) {
      await cleanup(request);
    }
  });

  test('Search Message Subscriptions - by message key, single result - 200 Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              messageKey: state.messageKeyPrimary,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      await validateResponse(
        {
          path: CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(1);
      const subscription = json.items[0] as CorrelatedMessageSubscription;
      assertRequiredFields(
        subscription,
        correlatedMessageSubscriptionRequiredFields,
      );
      expect(subscription.messageKey).toBe(state.messageKeyPrimary);
      expect(subscription.tenantId).toBe('<default>');
      assertEqualsForKeys(
        subscription,
        expectedCorrelationSubscriptionSearchResponse1,
        [
          'correlationKey',
          'elementId',
          'messageName',
          'partitionId',
          'processDefinitionId',
          'tenantId',
        ],
      );
    }).toPass(defaultAssertionOptions);
  });

  test('Search Message Subscriptions - multiple result - 200 Success', async ({
    request,
  }) => {
    await expect(async () => {
      const res = await request.post(
        buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: {
            sort: [
              {
                field: 'correlationTime',
                order: 'DESC',
              },
            ],
            page: {limit: 50},
          },
        },
      );

      expect(res.status()).toBe(200);
      await validateResponse(
        {
          path: CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBeGreaterThanOrEqual(3);

      const subscriptions = json.items as CorrelatedMessageSubscription[];
      for (const subscription of subscriptions) {
        assertRequiredFields(
          subscription,
          correlatedMessageSubscriptionRequiredFields,
        );
      }

      const resultMessageKeys = subscriptions.map((s) => s.messageKey);
      expect(resultMessageKeys).toEqual(expect.arrayContaining(messageKeys));
    }).toPass(defaultAssertionOptions);
  });

  test('Search Message Subscriptions - no result - 200 Success', async ({
    request,
  }) => {
    const someNotExistingMessageKey = '9999999999999';
    await expect(async () => {
      const res = await request.post(
        buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              messageKey: someNotExistingMessageKey,
            },
          },
        },
      );
      expect(res.status()).toBe(200);
      await validateResponse(
        {
          path: CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT,
          method: 'POST',
          status: '200',
        },
        res,
      );

      const json = await res.json();
      assertRequiredFields(json, paginatedResponseFields);
      expect(json.page.totalItems).toBe(0);
    }).toPass(defaultAssertionOptions);
  });

  test('Search Message Subscriptions - multiple filter - 200 Success', async ({
    request,
  }) => {
    const processInstanceKeyToSearch = state.processInstance4;
    await test.step('Search by process instance key and tenant id', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: processInstanceKeyToSearch,
                tenantId: '<default>',
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(2);
        const subscriptions = json.items as CorrelatedMessageSubscription[];
        for (const subscription of subscriptions) {
          assertRequiredFields(
            subscription,
            correlatedMessageSubscriptionRequiredFields,
          );
          expect(subscription.processInstanceKey).toBe(
            processInstanceKeyToSearch,
          );
          expect(subscription.tenantId).toBe('<default>');
        }
      }).toPass(defaultAssertionOptions);
    });

    await test.step('Search by process instance key and correlationKey', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(),
            data: {
              filter: {
                processInstanceKey: processInstanceKeyToSearch,
                correlationKey: CORRELATE_MESSAGE_DOUBLE_2.correlationKey,
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT,
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
        assertRequiredFields(json, paginatedResponseFields);
        expect(json.page.totalItems).toBe(1);

        const subscription = json.items[0] as CorrelatedMessageSubscription;
        assertRequiredFields(
          subscription,
          correlatedMessageSubscriptionRequiredFields,
        );
        expect(subscription.processInstanceKey).toBe(
          processInstanceKeyToSearch,
        );
        expect(subscription.correlationKey).toBe(
          CORRELATE_MESSAGE_DOUBLE_2.correlationKey,
        );
      }).toPass(defaultAssertionOptions);
    });
  });

  test('Search Message Subscriptions - invalid filter field - 400 Bad Request', async ({
    request,
  }) => {
    const invalidField = 'invalidFieldMeow';
    const res = await request.post(
      buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            [invalidField]: 'someValue',
          },
        },
      },
    );
    await assertBadRequest(
      res,
      `Request property [filter.${invalidField}] cannot be parsed`,
    );
  });

  test('Search Message Subscriptions - invalid filter value - 400 Bad Request', async ({
    request,
  }) => {
    const invalidFieldValue = 'invalidMeow';
    const res = await request.post(
      buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: invalidFieldValue,
          },
        },
      },
    );
    await assertBadRequest(res, `For input string: \"${invalidFieldValue}\"`);
  });

  test('Search Message Subscriptions - 401 Unauthorized', async ({request}) => {
    const res = await request.post(
      buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
      {
        // No auth headers
        data: {
          filter: {
            messageKey: state.messageKeyPrimary,
          },
        },
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Search Message Subscriptions - Forbidden search - 200 Empty Results', async ({request}) => {
    let userWithResourcesAuthorizationToSendRequest: {
      username: string;
      name: string;
      email: string;
      password: string;
    } = {} as {
      username: string;
      name: string;
      email: string;
      password: string;
    };
    await test.step('Setup - Create user for authorization tests', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
      cleanups.push(async (request) => {
        await cleanupUsers(request, [
          userWithResourcesAuthorizationToSendRequest.username,
        ]);
      });
    });

    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );

    await test.step('Attempt to search correlated message subscriptions without proper authorization', async () => {
      await expect(async () => {
        const res = await request.post(
          buildUrl(CORRELATED_MESSAGE_SUBSCRIPTION_SEARCH_ENDPOINT),
          {
            headers: jsonHeaders(token), // overrides default demo:demo
            data: {
              filter: {
                messageKey: state.messageKeyPrimary,
              },
            },
          },
        );
        await assertStatusCode(res, 200);
        const json = await res.json();
        expect(json.page.totalItems).toBe(0);
      }).toPass(defaultAssertionOptions);
    });
  });
});
