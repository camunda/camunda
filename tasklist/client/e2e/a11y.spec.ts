/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/fixtures/test';
import schema from './resources/bigForm.json' assert {type: 'json'};

const MOCK_TENANT = {
  tenantId: 'tenantA',
  name: 'Tenant A',
};

const MOCK_TASK = {
  id: 'task123',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
  processDefinitionKey: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  priority: 50,
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
  tenantId: MOCK_TENANT.tenantId,
  context: null,
};

test.describe('a11y', () => {
  test('have no violations', async ({page, makeAxeBuilder}) => {
    await page.route(/^.*\/(v1|v2).*$/i, (route) => {
      if (route.request().url().includes('v1/tasks/task123/variables/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/tasks/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([
            {
              ...MOCK_TASK,
              isFirst: true,
              sortValues: ['1684878523864', '4503599627371430'],
            },
          ]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/tasks/task123')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(MOCK_TASK),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/forms/userTaskForm_1')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'userTaskForm_3j0n396',
            processDefinitionKey: '2251799813685255',
            schema: JSON.stringify(schema),
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v2/authentication/me')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            username: 'demo',
            displayName: 'demo',
            salesPlanType: null,
            roles: null,
            c8Links: [],
            tenants: [MOCK_TENANT],
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/');
    await page.getByText('Big form process').click();

    await expect(page.getByText('Title 1')).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });
});
