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
  cleanupAuthorizations,
  createComponentAuthorization,
  createInstanceOnceDeployed,
  createUser,
  grantUserResourceAuthorization,
  partitionOf,
  partitionsCount,
  searchElementInstanceByElementIdAndState,
  searchProcessInstances,
} from '@requestHelpers';

/* eslint-disable playwright/expect-expect */

const START_EVENT_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";
const CATCH_EVENT_FORBIDDEN_DETAIL =
  "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'";

test.describe.serial('Signal Permission API', () => {
  const prefix = uniquePrefixedId('signal-perm');
  const startProcessDefinitionId = `${prefix}-start`;
  const startSignalName = `${prefix}-StartSignal`;
  const catchProcessDefinitionId = `${prefix}-catch`;
  const catchSignalName = `${prefix}-CatchSignal`;

  const catchProcessInstanceKeys: string[] = [];
  const grantedAuthorizationKeys: string[] = [];

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

    const partitions = await partitionsCount(request);
    const coveredPartitions = new Set<number>();
    for (
      let attempt = 0;
      attempt < 5 * partitions && coveredPartitions.size < partitions;
      attempt++
    ) {
      const instance = await createInstanceOnceDeployed(
        catchProcessDefinitionId,
        1,
      );
      catchProcessInstanceKeys.push(instance.processInstanceKey);
      coveredPartitions.add(partitionOf(instance.processInstanceKey));
    }
    expect(
      coveredPartitions.size,
      'no waiting catch instance on some partition',
    ).toBe(partitions);

    for (const processInstanceKey of catchProcessInstanceKeys) {
      await expect(async () => {
        await searchElementInstanceByElementIdAndState(
          request,
          processInstanceKey,
          'catchSignal',
          'ACTIVE',
        );
      }).toPass(defaultAssertionOptions);
    }

    userWithoutPermissions = await createUser(request);
    await grantUserResourceAuthorization(request, userWithoutPermissions);
    userWithoutPermissionsToken = encode(
      `${userWithoutPermissions.username}:${userWithoutPermissions.password}`,
    );
  });

  test.afterAll(async ({request}) => {
    for (const processInstanceKey of catchProcessInstanceKeys) {
      await cancelProcessInstance(processInstanceKey);
    }
    await cleanupAuthorizations(request, grantedAuthorizationKeys);
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
    // given a process instance waiting on a signal catch event on every
    // partition and a user without UPDATE_PROCESS_INSTANCE on
    // PROCESS_DEFINITION
    // when the user broadcasts the matching signal
    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: jsonHeaders(userWithoutPermissionsToken),
      data: {signalName: catchSignalName},
    });

    // then the request is rejected with 403 FORBIDDEN
    await assertForbiddenRequest(res, CATCH_EVENT_FORBIDDEN_DETAIL);
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

  test('Broadcast signal to start event - succeeds once CREATE_PROCESS_INSTANCE is granted', async ({
    request,
  }) => {
    // given the same user, now granted CREATE_PROCESS_INSTANCE on the start
    // event process definition
    grantedAuthorizationKeys.push(
      await createComponentAuthorization(request, {
        ownerId: userWithoutPermissions.username,
        ownerType: 'USER',
        resourceId: startProcessDefinitionId,
        resourceType: 'PROCESS_DEFINITION',
        permissionTypes: ['CREATE_PROCESS_INSTANCE'],
      }),
    );

    // when the user broadcasts the start signal
    await expect(async () => {
      const res = await request.post(buildUrl('/signals/broadcast'), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {signalName: startSignalName},
      });
      await assertStatusCode(res, 200);
    }).toPass(defaultAssertionOptions);

    // then the signal starts a process instance
    await expect(async () => {
      const instances = await searchProcessInstances(request, {
        processDefinitionId: startProcessDefinitionId,
      });
      expect(instances.length).toBeGreaterThanOrEqual(1);
    }).toPass(defaultAssertionOptions);
  });

  test('Broadcast signal to intermediate catch event - succeeds once UPDATE_PROCESS_INSTANCE is granted', async ({
    request,
  }) => {
    // given the same user, now granted UPDATE_PROCESS_INSTANCE on the catch
    // event process definition
    grantedAuthorizationKeys.push(
      await createComponentAuthorization(request, {
        ownerId: userWithoutPermissions.username,
        ownerType: 'USER',
        resourceId: catchProcessDefinitionId,
        resourceType: 'PROCESS_DEFINITION',
        permissionTypes: ['UPDATE_PROCESS_INSTANCE'],
      }),
    );

    // when the user broadcasts the catch signal until every partition accepts it
    // then the signal is caught by the waiting instance on every partition
    await expect(async () => {
      const res = await request.post(buildUrl('/signals/broadcast'), {
        headers: jsonHeaders(userWithoutPermissionsToken),
        data: {signalName: catchSignalName},
      });
      await assertStatusCode(res, 200);
      const search = await request.post(buildUrl('/element-instances/search'), {
        headers: jsonHeaders(),
        data: {
          filter: {
            processDefinitionId: catchProcessDefinitionId,
            elementId: 'catchSignal',
            state: 'COMPLETED',
          },
        },
      });
      await assertStatusCode(search, 200);
      expect((await search.json()).items).toHaveLength(
        catchProcessInstanceKeys.length,
      );
    }).toPass(defaultAssertionOptions);
  });
});
