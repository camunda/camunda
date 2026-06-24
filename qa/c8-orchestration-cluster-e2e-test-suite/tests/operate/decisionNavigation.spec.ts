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
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {jsonHeaders} from 'utils/http';

type ProcessInstance = {
  processInstanceKey: string;
};

let processInstanceWithFailedDecision: ProcessInstance;
let processInstanceWithSuccessfulDecision: ProcessInstance;

test.beforeAll(async ({request}) => {
  await deploy([
    './resources/invoiceBusinessDecisions.dmn',
    './resources/invoice.bpmn',
  ]);

  processInstanceWithFailedDecision = await createSingleInstance('invoice', 1);

  await test.step('Create evaluated decision instance', async () => {
    processInstanceWithSuccessfulDecision = await createSingleInstance(
      'invoice',
      1,
      {amount: 500, invoiceCategory: 'Misc'},
    );
  });

  await test.step('Wait for failed instance to be indexed', async () => {
    await expect
      .poll(
        async () => {
          const response = await request.post('/v2/decision-instances/search', {
            headers: jsonHeaders(),
            data: {
              filter: {
                state: 'FAILED',
                processInstanceKey:
                  processInstanceWithFailedDecision.processInstanceKey,
              },
            },
          });
          if (response.status() !== 200) return 0;
          const result = await response.json();
          return result.page?.totalItems ?? 0;
        },
        {timeout: 60_000, intervals: [2_000, 5_000]},
      )
      .toBeGreaterThanOrEqual(1);
  });
});

test.describe('Decision Navigation', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
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
  }) => {
    const processInstanceKey =
      processInstanceWithFailedDecision.processInstanceKey;
    let calledDecisionInstanceId: string;

    await test.step('Navigate to process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });
    });

    await test.step('Verify process diagram is visible', async () => {
      await expect(operateProcessInstancePage.diagram).toBeInViewport();
      await expect(
        operateProcessInstancePage.getDiagramElement('Activity_1tjwahx'),
      ).toBeVisible();
    });

    await test.step('Wait for incidents tab to be visible', async () => {
      await expect(operateProcessInstancePage.incidentsTab).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Click on business rule task in diagram', async () => {
      await operateProcessInstancePage.clickDiagramElement('Activity_1tjwahx');
    });

    await test.step('Navigate to failing root cause decision', async () => {
      await operateProcessInstancePage.clickIncidentsTab();
      await operateProcessInstancePage.navigateToFailingElementForIncident(
        'Decision evaluation error.',
        'Invoice Classification',
      );
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

    await test.step('Filter decision instances by Invoice Classification', async () => {
      await operateDecisionsPage.selectDecisionName('Invoice Classification');
      // Wait for the filtered results to load — Assign Approver Group rows must be gone
      // before interacting, since both share the same processInstanceKey
      await expect(
        operateDecisionsPage.decisionInstancesList.getByText(
          'Assign Approver Group',
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
    operateDecisionsPage,
    operateDecisionInstancePage,
    operateHomePage,
  }) => {
    await test.step('Navigate to Decisions page', async () => {
      await operateHomePage.clickDecisionsTab();
    });

    await test.step('Filter by Assign Approver Group decision', async () => {
      await operateDecisionsPage.selectDecisionName('Assign Approver Group');
      await expect(operateDecisionsPage.decisionInstancesList).toBeVisible();
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('row'),
      ).not.toHaveCount(0);
    });

    await test.step('Open the first Assign Approver Group decision instance', async () => {
      await operateDecisionsPage.decisionInstancesList
        .getByRole('row')
        .filter({
          hasText: processInstanceWithSuccessfulDecision.processInstanceKey,
        })
        .getByRole('link', {name: /View decision instance/})
        .first()
        .click();
    });

    await test.step('Verify we are on the Assign Approver Group decision instance', async () => {
      await expect(operateDecisionInstancePage.decisionPanel).toBeVisible();
      // Input column header for Assign Approver Group is "Invoice Classification"
      await expect(
        operateDecisionInstancePage.decisionPanel.getByText(
          'Invoice Classification',
        ),
      ).toBeVisible();
    });

    await test.step('Verify the DRD panel is open', async () => {
      await expect(operateDecisionInstancePage.drd).toBeVisible();
    });

    await test.step('Click Invoice Classification node in the DRD', async () => {
      await operateDecisionInstancePage.clickDrdDecisionNode(
        'Invoice Classification',
      );
    });

    await test.step('Verify navigation to Invoice Classification instance with updated input data', async () => {
      await expect(operateDecisionInstancePage.decisionPanel).toBeVisible({
        timeout: 30000,
      });
      await expect(
        operateDecisionInstancePage.decisionPanel.getByText('Invoice Amount'),
      ).toBeVisible();
    });
  });
});
