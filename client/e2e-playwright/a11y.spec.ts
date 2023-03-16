/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';

import AxeBuilder from '@axe-core/playwright';

import schema from './bigForm.json';

const DEFAULT_AXE_RULES = [
  'best-practice',
  'wcag2a',
  'wcag2aa',
  'cat.semantics',
  'cat.forms',
];

test.describe('a11y', () => {
  test('have no violations', async ({page}) => {
    await page.route('**/graphql', (route) => {
      const {operationName} = route.request().postDataJSON();

      switch (operationName) {
        case 'GetCurrentUser':
          return route.fulfill({
            status: 200,
            body: JSON.stringify({
              data: {
                currentUser: {
                  userId: 'demo',
                  displayName: 'demo',
                  permissions: ['READ', 'WRITE'],
                  salesPlanType: null,
                  roles: null,
                  c8Links: [],
                  __typename: 'User',
                },
              },
            }),
          });
        case 'GetTasks':
          return route.fulfill({
            status: 200,
            body: JSON.stringify({
              data: {
                tasks: [
                  {
                    id: 'task123',
                    name: 'Big form task',
                    processName: 'Big form process',
                    assignee: 'demo',
                    creationTime: '2023-03-03T14:16:18.441+0100',
                    taskState: 'CREATED',
                    isFirst: true,
                  },
                ],
              },
            }),
          });
        case 'GetTask':
          return route.fulfill({
            status: 200,
            body: JSON.stringify({
              data: {
                task: {
                  id: 'task123',
                  formKey: 'camunda-forms:bpmn:userTaskForm_1',
                  processDefinitionId: '2251799813685255',
                  assignee: 'demo',
                  name: 'Big form task',
                  taskState: 'CREATED',
                  processName: 'Big form process',
                  creationTime: '2023-03-03T14:16:18.441+0100',
                  completionTime: null,
                },
              },
            }),
          });
        case 'GetForm':
          return route.fulfill({
            status: 200,
            body: JSON.stringify({
              data: {
                form: {
                  schema: JSON.stringify(schema),
                },
              },
            }),
          });
        case 'GetSelectedVariables':
          return route.fulfill({
            status: 200,
            body: JSON.stringify({
              data: {
                variables: [],
              },
            }),
          });
        default:
          return route.fulfill({
            status: 500,
            body: JSON.stringify({
              message: '',
            }),
          });
      }
    });

    await page.goto('/');
    await page.getByText('Big form process').click();

    const results = await new AxeBuilder({page})
      .withTags(DEFAULT_AXE_RULES)
      .analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });
});
