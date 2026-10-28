/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';
import {
  assertForbiddenRequest,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  CORRELATE_MESSAGE4,
  PUBLISH_NEW_MESSAGE,
} from '../../../../utils/beans/requestBeans';
import {
  cancelProcessInstance,
  createInstances,
  deploy,
} from '../../../../utils/zeebeClient';
import {createUser, grantUserResourceAuthorization} from '@requestHelpers';

const PUBLISH_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'CREATE' on resource 'MESSAGE'";
const CORRELATE_UPDATE_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";
const CORRELATE_CREATE_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";

const MESSAGE_START_PROCESS_RESOURCE =
  './resources/message_start_business_id_process.bpmn';
const START_MESSAGE_NAME = 'start_business_id_msg';

test.describe.serial('Message Permission API - Forbidden', () => {
  const state: Record<string, unknown> = {};

  let userWithoutPermissions: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  let userWithoutPermissionsToken: string;

  test.beforeAll(async ({request}) => {
    await deploy(['./resources/messageCatchEvent4.bpmn']);
    const processes = await createInstances('messageCatchEvent4', 1, 1);
    state['processInstanceKey'] = processes[0].processInstanceKey;

    await deploy([MESSAGE_START_PROCESS_RESOURCE]);

    userWithoutPermissions = await createUser(request);
    await grantUserResourceAuthorization(request, userWithoutPermissions);
    userWithoutPermissionsToken = encode(
      `${userWithoutPermissions.username}:${userWithoutPermissions.password}`,
    );
  });

  test.afterAll(async ({request}) => {
    await cancelProcessInstance(state['processInstanceKey'] as string);
    await cleanupUsers(request, [userWithoutPermissions.username]);
  });

  test('Publish message - forbidden without MESSAGE CREATE permission', async ({
    request,
  }) => {
    // given a user with only RESOURCE READ permission
    // when the user publishes a message
    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(userWithoutPermissionsToken),
      data: PUBLISH_NEW_MESSAGE(),
    });

    // then the request is rejected with 403 FORBIDDEN
    await assertForbiddenRequest(res, PUBLISH_FORBIDDEN_DETAIL);
  });

  test('Correlate message to intermediate event - forbidden without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given a process instance waiting on a message catch event and a user
    // without UPDATE_PROCESS_INSTANCE on PROCESS_DEFINITION
    // when the user correlates the matching message
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(userWithoutPermissionsToken),
      data: CORRELATE_MESSAGE4,
    });

    // then the request is rejected with 403 FORBIDDEN
    await assertForbiddenRequest(res, CORRELATE_UPDATE_FORBIDDEN_DETAIL);
  });

  test('Correlate message to message start event - forbidden without CREATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given a message start event subscription and a user without
    // CREATE_PROCESS_INSTANCE on PROCESS_DEFINITION
    // when the user correlates the start event message
    const res = await request.post(buildUrl('/messages/correlation'), {
      headers: jsonHeaders(userWithoutPermissionsToken),
      data: {name: START_MESSAGE_NAME, correlationKey: ''},
    });

    // then the request is rejected with 403 FORBIDDEN
    await assertForbiddenRequest(res, CORRELATE_CREATE_FORBIDDEN_DETAIL);
  });
});
