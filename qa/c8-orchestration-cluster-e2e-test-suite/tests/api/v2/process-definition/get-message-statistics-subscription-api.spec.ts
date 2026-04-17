/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {
  assertBadRequest,
  assertInvalidArgument,
  assertStatusCode,
  assertUnauthorizedRequest,
  buildUrl,
  jsonHeaders,
  encode,
} from '../../../../utils/http';
import {validateResponse} from '../../../../json-body-assertions';
import {cleanupUsers} from 'utils/usersCleanup';
import {
  CORRELATE_MESSAGE,
  CORRELATE_MESSAGE1,
  CORRELATE_MESSAGE2,
  CORRELATE_MESSAGE_DOUBLE_1,
  CORRELATE_MESSAGE_DOUBLE_2,
} from '../../../../utils/beans/requestBeans';
import {
  expectProcessInstanceCanBeFound,
  searchCorrelatedMessageSubscriptions,
  grantUserResourceAuthorization,
  createUser,
} from '@requestHelpers';

test.describe.parallel('Get message subscription statistics API Tests', () => {
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
  let processInstanceKeys: string[] = [];
  let messageKeys: string[] = [];
  const processDefinitionId = 'messageCatchEvent1';

  test.beforeAll(async ({request}) => {
    await test.step('Setup - Create test user with Resource Authorization and user for granting Authorization', async () => {
      userWithResourcesAuthorizationToSendRequest = await createUser(request);
      await grantUserResourceAuthorization(
        request,
        userWithResourcesAuthorizationToSendRequest,
      );
    });

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
      processInstanceKeys.push(procA.processInstanceKey);
      processInstanceKeys.push(procB.processInstanceKey);
      processInstanceKeys.push(procC.processInstanceKey);
      processInstanceKeys.push(procD.processInstanceKey);
    });

    await test.step('Poll process instances', async () => {
      for (const key of processInstanceKeys) {
        await expectProcessInstanceCanBeFound(request, key);
      }
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
        await assertStatusCode(res, 200);
        await validateResponse(
          {
            path: '/messages/correlation',
            method: 'POST',
            status: '200',
          },
          res,
        );
        const json = await res.json();
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
    await test.step('Cleanup - Delete test users', async () => {
      await cleanupUsers(request, [
        userWithResourcesAuthorizationToSendRequest.username,
      ]);
    });

    await test.step('Cancel process instances', async () => {
      for (const key of processInstanceKeys) {
        await cancelProcessInstance(key);
      }
    });

    await test.step('Clean arrays', async () => {
      processInstanceKeys = [];
      messageKeys = [];
    });
  });

  test('Get message subscription statistics - Success', async ({request}) => {
    const res = await request.post(
      buildUrl('/process-definitions/statistics/message-subscriptions'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {},
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/message-subscriptions',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBeGreaterThanOrEqual(1);
    expect(body.items.length).toBeGreaterThanOrEqual(1);
  });

  test('Get message subscription statistics filter by processDefinitionId - Success', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-definitions/statistics/message-subscriptions'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId,
          },
        },
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/message-subscriptions',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBe(1);
    expect(body.items.length).toBe(1);
    expect(body.items[0].processDefinitionId).toBe(processDefinitionId);
  });

  test('Get message subscription statistics by process instance key - Success', async ({
    request,
  }) => {
    const processInstanceKey = processInstanceKeys[1];
    let processDefinitionKey: string = '';

    await test.step('Get definitionKEy of process instance', async () => {
      const res = await request.get(
        buildUrl(`/process-instances/${processInstanceKey}`),
        {
          headers: jsonHeaders(),
        },
      );
      await assertStatusCode(res, 200);
      await validateResponse(
        {
          path: '/process-instances/{processInstanceKey}',
          method: 'GET',
          status: '200',
        },
        res,
      );
      const body = await res.json();
      processDefinitionKey = body.processDefinitionKey;
    });

    await test.step('Get message subscription statistics by process instance key', async () => {
      const res = await request.post(
        buildUrl('/process-definitions/statistics/message-subscriptions'),
        {
          headers: jsonHeaders(),
          data: {
            filter: {
              processInstanceKey,
            },
          },
        },
      );
      await assertStatusCode(res, 200);
      const body = await res.json();
      await validateResponse(
        {
          path: '/process-definitions/statistics/message-subscriptions',
          method: 'POST',
          status: '200',
        },
        res,
      );
      expect(body.page.totalItems).toBe(1);
      expect(body.items.length).toBe(1);
      const item = body.items[0];
      expect(item.processDefinitionKey).toBe(processDefinitionKey);
      expect(item.processDefinitionId).toBe(processDefinitionId);
      expect(item.processInstancesWithActiveSubscriptions).toBe(1);
      expect(item.activeSubscriptions).toBe(1);
    });
  });

  test('Get message subscription statistics with not existing filter - Bad Request', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-definitions/statistics/message-subscriptions'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            nonExistingFilter: 'test',
          },
        },
      },
    );
    await assertBadRequest(
      res,
      'Request property [filter.nonExistingFilter] cannot be parsed',
    );
  });

  test('Get message subscription statistics with not valid process instance key - Invalid Argument', async ({
    request,
  }) => {
    const wrongProcessInstanceKey = 'abc';
    const res = await request.post(
      buildUrl('/process-definitions/statistics/message-subscriptions'),
      {
        headers: jsonHeaders(),
        data: {
          filter: {
            processInstanceKey: wrongProcessInstanceKey,
          },
        },
      },
    );
    await assertInvalidArgument(
      res,
      400,
      `The provided processInstanceKey '${wrongProcessInstanceKey}' is not a valid key.`,
    );
  });

  test('Get message subscription statistics without authorization - Unauthorized', async ({
    request,
  }) => {
    const res = await request.post(
      buildUrl('/process-definitions/statistics/message-subscriptions'),
      {
        headers: {},
      },
    );
    await assertUnauthorizedRequest(res);
  });

  test('Get message subscription statistics - Forbidden, 200, empty response', async ({
    request,
  }) => {
    const token = encode(
      `${userWithResourcesAuthorizationToSendRequest.username}:${userWithResourcesAuthorizationToSendRequest.password}`,
    );
    const res = await request.post(
      buildUrl('/process-definitions/statistics/message-subscriptions'),
      {
        headers: jsonHeaders(token),
      },
    );
    await assertStatusCode(res, 200);
    const body = await res.json();
    await validateResponse(
      {
        path: '/process-definitions/statistics/message-subscriptions',
        method: 'POST',
        status: '200',
      },
      res,
    );
    expect(body.page.totalItems).toBe(0);
    expect(body.items.length).toBe(0);
  });
});
