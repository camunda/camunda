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
      // The full round trip (find row -> click -> land on the detail page)
      // is retried as one unit, not as separate steps each retried in
      // isolation. Confirmed in CI: clicking can pass the URL check
      // (navigates to /operate/decisions/<id>) and then Operate redirects
      // back to the decisions list — presumably because that specific
      // decision instance isn't queryable yet — which a URL-only check
      // doesn't catch, and which reloading *that* page can't fix since
      // reload just reloads the list you already bounced back to. Redoing
      // the whole thing from a freshly-filtered list is what actually
      // recovers.
      await waitForAssertion({
        assertion: async () => {
          await expect(decisionInstanceLink).toBeVisible({timeout: 10_000});
          // The decision instances list re-renders as Operate polls for live
          // data, which can detach the anchor between mouse-down and the
          // browser's default navigation and silently swallow the click (the
          // row highlights on hover but the URL never changes). Re-clicking
          // via the outer retry doesn't help because that path re-applies the
          // name filter first, so every click lands in the middle of another
          // filter-triggered re-render. Retry only the click here — no
          // re-filtering between attempts — so once the list settles a click
          // lands on a stable node and navigation actually happens.
          await expect(async () => {
            await decisionInstanceLink.click();
            await expect(page).toHaveURL(/\/operate\/decisions\/[^/?]+/, {
              timeout: 2_000,
            });
          }).toPass({timeout: 20_000});
          // The URL check alone isn't proof we're still there: Operate can
          // redirect back to the list right after this if the decision
          // instance isn't ready yet.
          await expect(operateDecisionInstancePage.decisionPanel).toBeVisible({
            timeout: 10_000,
          });
        },
        onFailure: async () => {
          // A failed attempt can leave us on either page: the decisions list
          // (if the row/click itself failed) or a decision instance detail
          // page (if decisionPanel was just the slow part, or Operate
          // redirected there before bouncing back). selectDecisionName()
          // needs the list's Name combobox, which the detail page doesn't
          // have — clickDecisionsTab() first guarantees we land on the list
          // regardless of which page we're actually on, via the persistent
          // nav bar rather than assuming reload's target. Then reload, since
          // the name filter is client-side React state, not a URL parameter
          // (confirmed via a failure snapshot showing the empty Name
          // combobox after a reload that didn't navigate first), so it needs
          // re-applying every time regardless.
          await operateHomePage.clickDecisionsTab();
          await page.reload();
          await operateDecisionsPage.selectDecisionName(
            'Assign Approver Group Navigation',
          );
        },
        maxRetries: 8,
      });
    });

    await test.step('Verify we are on the Assign Approver Group Navigation decision instance', async () => {
      // decisionPanel visibility is already confirmed by the previous step's
      // retry loop; this just checks the panel content. Input column header
      // for Assign Approver Group Navigation is "Invoice Classification" —
      // the DMN's input *label* was deliberately left unrenamed when
      // invoiceBusinessDecisionsNavigation.dmn was created (only decision
      // ids/names were suffixed "Navigation" for cross-spec isolation, since
      // labels don't cause naming collisions).
      await expect(
        operateDecisionInstancePage.decisionPanel.getByText(
          'Invoice Classification',
        ),
      ).toBeVisible();
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
