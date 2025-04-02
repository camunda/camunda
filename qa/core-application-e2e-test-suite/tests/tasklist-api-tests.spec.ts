/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {authAPI, pollAPI} from 'utils/apiHelpers';
import {createInstances, deploy} from 'utils/zeebeClient';
import {} from 'utils/apiHelpers';

const baseURL = process.env.CORE_APPLICATION_TASKLIST_URL;

test.beforeAll(async () => {
  await Promise.all([
    deploy([
      './resources/User_Task_Process_With_Form_API.bpmn',
      './resources/New Form.form',
    ]),
  ]);
  await createInstances('Form_User_Task_API', 1, 3);
  await authAPI('demo', 'demo', 'tasklist');
});

test.describe('API tests', () => {
  test.use({
    storageState: 'utils/.auth_tasklist',
    baseURL: baseURL,
  });

  test('Search for tasks', async ({request}) => {
    const taskList = await request.post('/v1/tasks/search');
    expect(taskList.status()).toBe(200);
  });

  test('Get a task via ID', async ({request}) => {
    // Poll until tasks are available
    const taskData = await pollAPI<{id: string}[]>(
      request,
      '/v1/tasks/search',
      (response) => response.length > 0, // Directly check length of the array
    );
    // Fetch details of the first task using its ID
    const response = await request.get(`/v1/tasks/${taskData[0].id}`);
    expect(response.status()).toBe(200);
  });
});
