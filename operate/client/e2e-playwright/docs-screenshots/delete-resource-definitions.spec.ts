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

import {test} from '../test-fixtures';

import {
  mockGroupedProcesses,
  mockResponses as mockProcessesResponses,
  mockDeleteProcess,
} from '../mocks/processes.mocks';
import {
  mockDecisionXml,
  mockResponses as mockDecisionsResponses,
  mockDeleteDecision,
  mockGroupedDecisions,
} from '../mocks/decisions.mocks';
import {open} from 'modules/mocks/diagrams';
import {expect} from '@playwright/test';

test.describe('delete resource definitions', () => {
  test('delete process definitions', async ({
    context,
    page,
    commonPage,
    processesPage,
  }) => {
    await commonPage.mockClientConfig(context);

    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: [],
        processInstances: {
          totalCount: 0,
          processInstances: [],
        },
        statistics: [],
        processXml: open('orderProcess.bpmn'),
        deleteProcess: mockDeleteProcess,
      }),
    );

    await page.setViewportSize({width: 1650, height: 900});

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
        canceled: 'true',
        completed: 'true',
      },
      options: {
        waitUntil: 'networkidle',
      },
    });

    await commonPage.addLeftArrow(processesPage.processNameFilter);
    await commonPage.addLeftArrow(processesPage.processVersionFilter);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-filters.png',
    });

    await commonPage.deleteArrows();

    await processesPage.navigateToProcesses({
      searchParams: {
        process: 'orderProcess',
        version: '1',
        active: 'true',
        incidents: 'true',
        canceled: 'true',
        completed: 'true',
      },
      options: {
        waitUntil: 'networkidle',
      },
    });

    await commonPage.addRightArrow(processesPage.deleteResourceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-button.png',
    });

    await commonPage.deleteArrows();

    await commonPage.disableModalAnimation();
    await processesPage.deleteResourceButton.click();

    await processesPage.deleteResourceModal.confirmCheckbox.check({
      force: true,
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-modal.png',
    });

    await processesPage.deleteResourceModal.confirmButton.click();

    await commonPage.addUpArrow(page.getByRole('progressbar'));

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/process-operations-panel.png',
    });
  });

  test('delete decision definitions', async ({
    context,
    page,
    commonPage,
    decisionsPage,
  }) => {
    await commonPage.mockClientConfig(context);

    await page.route(
      /^.*\/api.*$/i,
      mockDecisionsResponses({
        groupedDecisions: mockGroupedDecisions,
        batchOperations: [],
        decisionInstances: {totalCount: 0, decisionInstances: []},
        decisionXml: mockDecisionXml,
        deleteDecision: mockDeleteDecision,
      }),
    );

    await page.setViewportSize({width: 1650, height: 900});

    await decisionsPage.navigateToDecisions({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
      },
      options: {
        waitUntil: 'networkidle',
      },
    });

    await commonPage.addLeftArrow(decisionsPage.decisionNameFilter);
    await commonPage.addLeftArrow(decisionsPage.decisionVersionFilter);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-filters.png',
    });

    await commonPage.deleteArrows();

    await decisionsPage.navigateToDecisions({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
        name: 'invoiceClassification',
        version: '2',
      },
      options: {
        waitUntil: 'networkidle',
      },
    });

    await commonPage.addRightArrow(decisionsPage.deleteResourceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-button.png',
    });

    await commonPage.deleteArrows();

    await commonPage.disableModalAnimation();
    await decisionsPage.deleteResourceButton.click();

    await expect(
      decisionsPage.deleteResourceModal.confirmCheckbox,
    ).toBeVisible();

    await decisionsPage.deleteResourceModal.confirmCheckbox.click({
      force: true,
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-modal.png',
    });

    await decisionsPage.deleteResourceModal.confirmButton.click();

    await commonPage.addUpArrow(page.getByRole('progressbar'));

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/delete-resources/decision-operations-panel.png',
    });
  });
});
