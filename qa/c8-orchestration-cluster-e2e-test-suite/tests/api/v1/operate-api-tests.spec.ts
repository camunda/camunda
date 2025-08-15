/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {createInstances, deploy} from 'utils/zeebeClient';

test.beforeAll(async () => {
  await Promise.all([
    deploy(['./resources/User_Task_Process_With_Form_API.bpmn']),
  ]);
  await createInstances('Form_User_Task_API', 1, 3);
});

test.describe('API tests', () => {
  const auth = Buffer.from(`demo:demo`).toString('base64');
  const requestHeaders = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Basic ${auth}`,
    },
  };

  test('Search for process definitions', async ({request}) => {
    const processDefinition = await request.post(
      '/v1/process-definitions/search',
      requestHeaders,
    );
    expect(processDefinition.status()).toBe(200);
  });

  test('Get a process definition via key', async ({request}) => {
    let processDefinitions: {items: {key: number}[]} = {items: []};
    await expect(async () => {
      const response = await request.post(
        '/v1/process-definitions/search',
        requestHeaders,
      );
      expect(response.status()).toBe(200);
      processDefinitions = await response.json();
      expect(processDefinitions.items.length).toBeGreaterThan(0);
    }).toPass({
      intervals: [3_000, 4_000, 5_000],
      timeout: 30_000,
    });
    const response = await request.get(
      `/v1/process-definitions/${processDefinitions.items[0].key}`,
      requestHeaders,
    );
    expect(response.status()).toBe(200);
  });

  test('Search for process instances', async ({request}) => {
    const processInstancesList = await request.post(
      'v1/process-instances/search',
      requestHeaders,
    );
    expect(processInstancesList.status()).toBe(200);
  });

  test('Search for flownode-instances', async ({request}) => {
    const flowNodeInstancesList = await request.post(
      'v1/flownode-instances/search',
      requestHeaders,
    );
    expect(flowNodeInstancesList.status()).toBe(200);
  });

  test('Search for variables for process instances', async ({request}) => {
    const variablesInstancesList = await request.post(
      'v1/variables/search',
      requestHeaders,
    );
    expect(variablesInstancesList.status()).toBe(200);
  });

  test('Search for incidents', async ({request}) => {
    const incidentsList = await request.post(
      'v1/incidents/search',
      requestHeaders,
    );
    expect(incidentsList.status()).toBe(200);
  });
});
