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
  deployWithSubstitutions,
} from '../../../../utils/zeebeClient';
import {
  assertForbiddenRequest,
  assertStatusCode,
  buildUrl,
  encode,
  jsonHeaders,
} from '../../../../utils/http';
import {
  defaultAssertionOptions,
  uniquePrefixedId,
} from '../../../../utils/constants';
import {cleanupUsers} from '../../../../utils/usersCleanup';
import {
  createInstanceOnceDeployed,
  createUser,
  grantUserResourceAuthorization,
  searchElementInstanceByElementIdAndState,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */

const START_EVENT_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";
const CATCH_EVENT_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";

test.describe.serial('Signal Permission API - Forbidden', () => {
  const prefix = uniquePrefixedId('signal-perm');
  const startProcessDefinitionId = `${prefix}-start`;
  const startSignalName = `${prefix}-StartSignal`;
  const catchProcessDefinitionId = `${prefix}-catch`;
  const catchSignalName = `${prefix}-CatchSignal`;

  let catchProcessInstanceKey: string;

  let userWithoutPermissions: {
    username: string;
    name: string;
    email: string;
    password: string;
  };
  let userWithoutPermissionsToken: string;

  test.beforeAll(async ({request}) => {
    await deployWithSubstitutions(
      './resources/signal_start_permission_process.bpmn',
      {
        'id="signal_start_permission_process"': `id="${startProcessDefinitionId}"`,
        'name="SignalStartPermissionSignal"': `name="${startSignalName}"`,
      },
    );

    await deployWithSubstitutions(
      './resources/signal_catch_with_job_process.bpmn',
      {
        'id="sr_p3_signal"': `id="${catchProcessDefinitionId}"`,
        'name="SrP3Signal"': `name="${catchSignalName}"`,
        'type="sr-p3-signal-done"': `type="${prefix}-signal-done"`,
      },
    );
    const instance = await createInstanceOnceDeployed(
      catchProcessDefinitionId,
      1,
    );
    catchProcessInstanceKey = instance.processInstanceKey;
    await expect(async () => {
      await searchElementInstanceByElementIdAndState(
        request,
        catchProcessInstanceKey,
        'catchSignal',
        'ACTIVE',
      );
    }).toPass(defaultAssertionOptions);

    userWithoutPermissions = await createUser(request);
    await grantUserResourceAuthorization(request, userWithoutPermissions);
    userWithoutPermissionsToken = encode(
      `${userWithoutPermissions.username}:${userWithoutPermissions.password}`,
    );
  });

  test.afterAll(async ({request}) => {
    await cancelProcessInstance(catchProcessInstanceKey);
    await cleanupUsers(request, [userWithoutPermissions.username]);
  });

  test('Broadcast signal to start event - forbidden without CREATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given a deployed process with a signal start event and a user without
    // CREATE_PROCESS_INSTANCE on PROCESS_DEFINITION
    // when the user broadcasts the start signal
    // then the request is rejected with 403 FORBIDDEN
    await expect(async () => {
      const res = await request.post(buildUrl('/signals/broadcast'), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {signalName: startSignalName},
      });
      await assertForbiddenRequest(res, START_EVENT_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Broadcast signal to intermediate catch event - forbidden without UPDATE_PROCESS_INSTANCE permission', async ({
    request,
  }) => {
    // given a process instance waiting on a signal catch event and a user
    // without UPDATE_PROCESS_INSTANCE on PROCESS_DEFINITION
    // when the user broadcasts the matching signal
    // then the request is rejected with 403 FORBIDDEN
    await expect(async () => {
      const res = await request.post(buildUrl('/signals/broadcast'), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {signalName: catchSignalName},
      });
      await assertForbiddenRequest(res, CATCH_EVENT_FORBIDDEN_DETAIL);
    }).toPass(defaultAssertionOptions);
  });

  test('Broadcast signal without matching subscription - succeeds without process permissions', async ({
    request,
  }) => {
    // given a user without any PROCESS_DEFINITION permission and a signal
    // name no process subscribes to
    // when the user broadcasts that signal
    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: jsonHeaders(userWithoutPermissionsToken),
      data: {signalName: `${prefix}-UnsubscribedSignal`},
    });

    // then the request succeeds, as permissions are checked per matching
    // subscription rather than for the broadcast itself
    await assertStatusCode(res, 200);
  });
});
