/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {expect} from '@playwright/test';
import {test} from './test-fixtures';
import schema from './resources/bigForm.json' assert {type: 'json'};

const MOCK_TENANT = {
  id: 'tenantA',
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
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
  tenantId: MOCK_TENANT.id,
  context: null,
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
            tenants: [MOCK_TENANT],
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

    await expect(page.getByText('Title 1')).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });
});
