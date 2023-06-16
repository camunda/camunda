/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from './axe-test';
import schema from './resources/bigForm.json';

const MOCK_TASK = {
  id: 'task123',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  processDefinitionKey: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
};

test.describe('a11y', () => {
  test('have no violations', async ({page, makeAxeBuilder}) => {
    await page.route(/^.*\/v1.*$/i, (route) => {
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

      if (route.request().url().includes('v1/internal/users/current')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            userId: 'demo',
            displayName: 'demo',
            permissions: ['READ', 'WRITE'],
            salesPlanType: null,
            roles: null,
            c8Links: [],
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      route.continue();
    });

    await page.goto('/');
    await page.getByText('Big form process').click();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });
});
