/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test, MOCK_TENANTS} from '@/fixtures/v2-visual';
import {unassignedTask} from '@/mocks/v2/task';
import schema from './resources/bigForm.json' assert {type: 'json'};

test.describe('a11y', () => {
  test('have no violations', async ({
    page,
    makeAxeBuilder,
    mockQueryUserTasksRequest,
    mockGetUserTaskRequest,
    mockQueryVariablesByUserTaskRequest,
    mockGetUserTaskFormRequest,
  }) => {
    const MOCK_FORM_TASK = unassignedTask({
      formKey: 'bigForm',
      assignee: 'demo',
      name: 'Big form task',
      processName: 'Big form process',
      processDefinitionKey: '2251799813685255',
      elementId: 'Activity_0aecztp',
      processInstanceKey: '4503599627371425',
      tenantId: MOCK_TENANTS[0].tenantId,
    });

    mockQueryUserTasksRequest([MOCK_FORM_TASK]);
    mockGetUserTaskRequest(MOCK_FORM_TASK);
    mockQueryVariablesByUserTaskRequest({
      userTaskKey: MOCK_FORM_TASK.userTaskKey,
      variables: [],
    });
    mockGetUserTaskFormRequest({
      userTaskKey: MOCK_FORM_TASK.userTaskKey,
      form: {
        schema: JSON.stringify(schema),
        formKey: 'bigForm',
        version: 1,
        tenantId: MOCK_TENANTS[0].tenantId,
      },
    });

    await page.goto('/');
    await page.getByText('Big form process').click();

    await expect(page.getByText('Title 1')).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });
});
