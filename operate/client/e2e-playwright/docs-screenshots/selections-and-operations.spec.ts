/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {test} from '../test-fixtures';

import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockStatistics,
  mockResponses as mockProcessesResponses,
  mockOrderProcessInstances,
} from '../mocks/processes.mocks';
import {open} from 'modules/mocks/diagrams';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('selections and operations', () => {
  test('select operations to retry', async ({
    page,
    commonPage,
    processesPage,
  }) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockOrderProcessInstances,
        statistics: mockStatistics,
        processXml: open('orderProcess.bpmn'),
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
      options: {waitUntil: 'networkidle'},
    });

    await processesPage.selectProcess('Order process');
    await processesPage.selectVersion('1');
    await processesPage.processVersionFilter.blur();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/selections-and-operations/operate-many-instances-with-incident.png',
    });

    const checkboxes = page.getByRole('row', {name: /select row/i});
    await checkboxes.nth(1).locator('label').click();
    await checkboxes.nth(2).locator('label').click();
    await checkboxes.nth(3).locator('label').click();

    const retryButton = await page.getByRole('button', {
      name: 'Retry',
      exact: true,
    });

    await commonPage.addDownArrow(retryButton);
    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/selections-and-operations/operate-select-operation.png',
    });
  });

  test('view operations panel after retry operation', async ({
    page,
    commonPage,
    processesPage,
  }) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockOrderProcessInstances,
        statistics: mockStatistics,
        processXml: open('orderProcess.bpmn'),
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
      options: {waitUntil: 'networkidle'},
    });

    await processesPage.selectProcess('Order process');
    await processesPage.selectVersion('1');
    await processesPage.processVersionFilter.blur();

    await commonPage.expandOperationsPanel();

    await processesPage.diagram.moveCanvasHorizontally(-200);
    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/selections-and-operations/operate-operations-panel.png',
    });
  });
});
