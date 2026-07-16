/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createSingleInstance} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';

type ProcessInstance = {
  processInstanceKey: string;
};

let processInstanceWithFailedDecision: ProcessInstance;
let processInstanceWithSuccessfulDecision: ProcessInstance;
let calledDecisionInstanceId: string;

// Uses its own copy of the invoice DMN/BPMN (process id "invoiceNavigation",
// decisions suffixed "Navigation") rather than the shared ./resources/invoice.bpmn
// + invoiceBusinessDecisions.dmn also deployed by decisionFilters.spec.ts.
// Both specs' instances previously landed under the same "Assign Approver
// Group" decision name in Operate's shared decisions view, adding noise that
// could push this spec's own row out of the rendered window under load.
test.beforeAll(async () => {
  await deploy([
    './resources/invoiceBusinessDecisionsNavigation.dmn',
    './resources/invoiceNavigation.bpmn',
  ]);

  // No variables → decision evaluation error → FAILED decision instance
  processInstanceWithFailedDecision = await createSingleInstance(
    'invoiceNavigation',
    1,
  );

  // Valid inputs → decision evaluates successfully → EVALUATED decision instance
  processInstanceWithSuccessfulDecision = await createSingleInstance(
    'invoiceNavigation',
    1,
    {amount: 500, invoiceCategory: 'Misc'},
  );

  await sleep(2000);
});

test.describe('Decision Navigation', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Navigation between process and decision', async ({
    operateProcessInstancePage,
    operateDecisionInstancePage,
    operateDecisionsPage,
    operateHomePage,
    operateDiagramPage,
  }) => {
    const processInstanceKey =
      processInstanceWithFailedDecision.processInstanceKey;

    await test.step('Navigate to process instance', async () => {
      await operateProcessInstancePage.navigateToProcessInstance(
        processInstanceKey,
      );
    });

    await test.step('Verify process diagram is visible', async () => {
      await expect(operateProcessInstancePage.diagram).toBeInViewport();
      await expect(
        operateProcessInstancePage.getDiagramElement('Activity_1tjwahx'),
      ).toBeVisible();
    });

    await test.step('Wait for incidents banner to be visible', async () => {
      await expect
        .poll(
          async () => {
            return await operateProcessInstancePage.incidentsBanner.isVisible();
          },
          {timeout: 30000},
        )
        .toBe(true);
    });

    await test.step('Click on business rule task in diagram', async () => {
      await operateProcessInstancePage.clickDiagramElement('Activity_1tjwahx');
    });

    await test.step('Verify popover appears and navigate to decision', async () => {
      await expect(operateDiagramPage.popover).toBeVisible();
      await operateDiagramPage.clickViewRootCauseDecisionLink();
    });

    await test.step('Verify decision panel is visible', async () => {
      await expect(operateDecisionInstancePage.decisionPanel).toBeVisible();
      await expect(
        operateDecisionInstancePage.decisionPanel.getByText('Invoice Amount'),
      ).toBeVisible();
    });

    await test.step('Get decision instance ID', async () => {
      calledDecisionInstanceId =
        await operateDecisionInstancePage.getDecisionInstanceId();
    });

    await test.step('Close decision panel and return to process', async () => {
      await operateDecisionInstancePage.closeDrdPanel();
      await operateDecisionInstancePage.clickViewProcessInstanceLink(
        processInstanceKey,
      );
    });

    await test.step('Verify back to process instance view', async () => {
      await expect(operateProcessInstancePage.instanceHeader).toBeVisible();

      await expect(
        operateProcessInstancePage.instanceHeader.getByText(
          processInstanceKey,
          {exact: true},
        ),
      ).toBeVisible();

      await expect(operateProcessInstancePage.diagram).toBeInViewport();

      await expect(
        operateProcessInstancePage.getDiagramElement('Activity_1tjwahx'),
      ).toBeVisible();
    });

    await test.step('Navigate to decisions section', async () => {
      await operateHomePage.clickDecisionsTab();
    });

    await test.step('View specific decision instance', async () => {
      await operateDecisionsPage.clickViewDecisionInstanceLink(
        calledDecisionInstanceId,
      );
    });

    await test.step('Verify decision panel is visible again', async () => {
      await expect(operateDecisionInstancePage.decisionPanel).toBeVisible();
      await expect(
        operateDecisionInstancePage.decisionPanel.getByText('Invoice Amount'),
      ).toBeVisible();
    });
  });

  test('should navigate to process instance from decision instances list', async ({
    operateDecisionsPage,
    operateProcessInstancePage,
    operateHomePage,
  }) => {
    const processInstanceKey =
      processInstanceWithSuccessfulDecision.processInstanceKey;

    await test.step('Navigate to Decisions page', async () => {
      await operateHomePage.clickDecisionsTab();
    });

    await test.step('Filter decision instances by Invoice Classification Navigation', async () => {
      await operateDecisionsPage.selectDecisionName(
        'Invoice Classification Navigation',
      );
      // Wait for the filtered results to load — Assign Approver Group Navigation rows must be gone
      // before interacting, since both share the same processInstanceKey
      await expect(
        operateDecisionsPage.decisionInstancesList.getByText(
          'Assign Approver Group Navigation',
        ),
      ).toHaveCount(0);
    });

    await test.step('Click the Process Instance Key link for the evaluated instance', async () => {
      await operateDecisionsPage.decisionInstancesList
        .getByRole('link', {
          name: `View process instance ${processInstanceKey}`,
        })
        .first()
        .click();
    });

    await test.step('Verify navigation to the corresponding process instance', async () => {
      await expect(operateProcessInstancePage.instanceHeader).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHeader.getByText(
          processInstanceKey,
          {
            exact: true,
          },
        ),
      ).toBeVisible();
    });
  });

  test('should navigate through DRD to another decision instance', async ({
    page,
    operateDecisionsPage,
    operateDecisionInstancePage,
    operateHomePage,
  }) => {
    await test.step('Navigate to Decisions page', async () => {
      await operateHomePage.clickDecisionsTab();
    });

    await test.step('Filter by Assign Approver Group Navigation decision', async () => {
      await operateDecisionsPage.selectDecisionName(
        'Assign Approver Group Navigation',
      );
      await expect(operateDecisionsPage.decisionInstancesList).toBeVisible();
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('row'),
      ).not.toHaveCount(0);
    });

    await test.step('Open the Assign Approver Group Navigation decision instance', async () => {
      // Scope to this run's Assign Approver Group Navigation row explicitly, so the correct
      // decision instance is selected even if the name filter has not fully applied.
      const decisionInstanceLink = operateDecisionsPage.decisionInstancesList
        .getByRole('row')
        .filter({
          hasText: processInstanceWithSuccessfulDecision.processInstanceKey,
        })
        .filter({hasText: 'Assign Approver Group Navigation'})
        .getByRole('link', {name: /View decision instance/})
        .first();
      // Wait for the freshly created decision instance to be indexed and
      // rendered. beforeAll creates two instances back-to-back (failed, then
      // successful); under load the second can still be unindexed when this
      // step runs even though this spec's own data no longer competes with
      // decisionFilters.spec.ts's. A single wait with no reload/retry can't
      // recover if indexing is still catching up when it expires.
      await waitForAssertion({
        assertion: async () => {
          await expect(decisionInstanceLink).toBeVisible({timeout: 10_000});
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 6,
      });
      // The decision instances list live-polls and re-renders, which can drop a
      // click before it navigates. Retry the click until the decision instance
      // page is actually opened.
      await waitForAssertion({
        assertion: async () => {
          await decisionInstanceLink.click();
          await expect(page).toHaveURL(/\/operate\/decisions\/[^/?]+/, {
            timeout: 10_000,
          });
        },
        onFailure: async () => {},
      });
    });

    await test.step('Verify we are on the Assign Approver Group Navigation decision instance', async () => {
      // Same import-lag hazard as the previous step: a single wait here (no
      // retry) was the exact assertion observed failing on a Playwright
      // retry attempt in CI, after the row-visibility wait above had already
      // succeeded — the decision instance page itself can still be slow to
      // populate right after navigating to it.
      await waitForAssertion({
        assertion: async () => {
          await expect(operateDecisionInstancePage.decisionPanel).toBeVisible({
            timeout: 10_000,
          });
          // Input column header for Assign Approver Group Navigation is "Invoice Classification Navigation"
          await expect(
            operateDecisionInstancePage.decisionPanel.getByText(
              'Invoice Classification Navigation',
            ),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 6,
      });
    });

    await test.step('Verify the DRD panel is open', async () => {
      await expect(operateDecisionInstancePage.drd).toBeVisible();
    });

    await test.step('Click Invoice Classification Navigation node in the DRD', async () => {
      await operateDecisionInstancePage.clickDrdDecisionNode(
        'Invoice Classification Navigation',
      );
    });

    await test.step('Verify navigation to Invoice Classification Navigation instance with updated input data', async () => {
      await expect(operateDecisionInstancePage.decisionPanel).toBeVisible({
        timeout: 30000,
      });
      await expect(
        operateDecisionInstancePage.decisionPanel.getByText('Invoice Amount'),
      ).toBeVisible();
    });
  });
});
