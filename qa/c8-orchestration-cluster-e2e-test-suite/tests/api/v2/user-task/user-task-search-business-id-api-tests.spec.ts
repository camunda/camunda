/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '@playwright/test';
import {cancelProcessInstance, deploy} from '../../../../utils/zeebeClient';
import {searchUserTasks, startInstanceWithBusinessId} from '@requestHelpers';
import {
  defaultAssertionOptions,
  generateUniqueId,
} from '../../../../utils/constants';

const USER_TASK_PROCESS_ID = 'user_task_api_test_process';

const runPrefix = `ut-${generateUniqueId()}`;
const BUSINESS_ID_A = `${runPrefix}-aaa`;
const BUSINESS_ID_B = `${runPrefix}-zzz`;

test.describe.parallel('Search User Task - Business ID API', () => {
  const state: Record<string, string> = {
    processInstanceKeyA: '',
    processInstanceKeyB: '',
  };

  test.beforeAll(async ({request}) => {
    await deploy(['./resources/user_task_api_test_process.bpmn']);

    state['processInstanceKeyA'] = await startInstanceWithBusinessId(
      request,
      USER_TASK_PROCESS_ID,
      BUSINESS_ID_A,
    );
    state['processInstanceKeyB'] = await startInstanceWithBusinessId(
      request,
      USER_TASK_PROCESS_ID,
      BUSINESS_ID_B,
    );

    await expect(async () => {
      const json = await searchUserTasks(request, {
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

  test('User task exposes and is filterable by the owning instance Business ID', async ({
    request,
  }) => {
    const json = await searchUserTasks(request, {businessId: BUSINESS_ID_A});
    expect(json.page.totalItems).toBe(1);
    const item = json.items[0];
    expect(item.businessId).toBe(BUSINESS_ID_A);
    expect(item.processInstanceKey).toBe(state['processInstanceKeyA']);
  });

  test('Filter user tasks by Business ID with a like wildcard returns both', async ({
    request,
  }) => {
    const json = await searchUserTasks(request, {
      businessId: {$like: `${runPrefix}-*`},
    });
    expect(json.page.totalItems).toBe(2);
    expect(
      json.items.map((i: {businessId: string}) => i.businessId).sort(),
    ).toEqual([BUSINESS_ID_A, BUSINESS_ID_B]);
  });

  test('Filter user tasks by a non-matching Business ID returns empty', async ({
    request,
  }) => {
    const json = await searchUserTasks(request, {
      businessId: `${runPrefix}-none`,
    });
    expect(json.page.totalItems).toBe(0);
  });

  test('Sort user tasks by Business ID ascending and descending', async ({
    request,
  }) => {
    const filter = {businessId: {$like: `${runPrefix}-*`}};

    const ascending = await searchUserTasks(request, filter, [
      {field: 'businessId', order: 'asc'},
    ]);
    expect(
      ascending.items.map((i: {businessId: string}) => i.businessId),
    ).toEqual([BUSINESS_ID_A, BUSINESS_ID_B]);

    const descending = await searchUserTasks(request, filter, [
      {field: 'businessId', order: 'desc'},
    ]);
    expect(
      descending.items.map((i: {businessId: string}) => i.businessId),
    ).toEqual([BUSINESS_ID_B, BUSINESS_ID_A]);
  });
});
