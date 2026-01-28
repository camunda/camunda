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
let calledDecisionInstanceId: string;

test.beforeAll(async () => {
  await deploy([
    './resources/invoiceBusinessDecisions.dmn',
    './resources/invoice.bpmn',
  ]);

  processInstanceWithFailedDecision = await createSingleInstance('invoice', 1);

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
      await waitForAssertion({
        assertion: async () => {
          await expect(operateDiagramPage.popover).toBeVisible();
        },
        onFailure: async () => {
          await operateProcessInstancePage.clickDiagramElement(
            'Activity_1tjwahx',
          );
          await operateProcessInstancePage.clickDiagramElement(
            'Activity_1tjwahx',
          );
        },
      });
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateDiagramPage.viewRootCauseDecisionLink,
          ).toBeVisible();
        },
        onFailure: async () => {
          await operateProcessInstancePage.clickDiagramElement(
            'Activity_1tjwahx',
          );
          await operateProcessInstancePage.clickDiagramElement(
            'Activity_1tjwahx',
          );
        },
      });
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
});
