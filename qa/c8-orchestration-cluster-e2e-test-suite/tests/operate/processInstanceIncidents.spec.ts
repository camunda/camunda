/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';

test.beforeAll(async () => {
  await deploy([
    './resources/decision_to_test-incident.dmn',
    './resources/process_to_test_incidents.bpmn',
    './resources/callProcess_with Incident.bpmn',
  ]);
  await createInstances('root-cause-test', 1, 1);
  await createInstances('call-level-1-process', 1, 1, {shouldFail: true});
});

test.describe('Process Instance Incident', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Verify Incident root cause instance', async ({
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
  }) => {
    test.slow();

    await test.step('Navigate to Processes tab and open the process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName('Process-Incident');
      await operateProcessesPage.clickProcessInstanceLink();
    });
    await test.step('Click IO mapping Error and verify Error Type', async () => {
      await operateProcessInstancePage.clickOnElementInDiagram(
        'Task_IOMapping',
      );

      await expect(operateProcessInstancePage.metadataPopover).toBeVisible({
        timeout: 10000,
      });

      await expect(operateProcessInstancePage.incidentSection).toBeVisible();
      await expect(
        operateProcessInstancePage.getIncidentErrorMessageByText(
          'IO mapping error.',
        ),
      ).toBeVisible();
      await operateProcessInstancePage.clickOnElementInDiagram(
        'Task_IOMapping',
      );
      await expect(operateProcessInstancePage.metadataPopover).toBeHidden({
        timeout: 10000,
      });
    });
    await test.step('Click ExclusiveGatewayError and verify Error Type', async () => {
      await operateProcessInstancePage.clickOnElementInDiagram(
        'Gateway_Expression',
      );

      await expect(operateProcessInstancePage.metadataPopover).toBeVisible({
        timeout: 10000,
      });

      await expect(operateProcessInstancePage.incidentSection).toBeVisible();
      await expect(
        operateProcessInstancePage.getIncidentErrorMessageByText(
          'Extract value error.',
        ),
      ).toBeVisible();
    });
    await operateProcessInstancePage.clickOnElementInDiagram(
      'Gateway_Expression',
    );
    await expect(operateProcessInstancePage.metadataPopover).toBeHidden({
      timeout: 10000,
    });
    await test.step('Click Call Activity and verify verify the error type', async () => {
      await operateProcessInstancePage.clickOnElementInDiagram(
        'Task_CallActivity',
      );

      await expect(operateProcessInstancePage.metadataPopover).toBeVisible({
        timeout: 10000,
      });

      // Verify incident section is visible
      await expect(operateProcessInstancePage.incidentSection).toBeVisible();

      // Verify Called  process name is displayed
      await expect(
        operateProcessInstancePage.rootCauseProcessName,
      ).toBeVisible();
      const rootCauseLink = operateProcessInstancePage.getCalledProcessLink(
        'View Call Activity Level 1',
      );
      await expect(rootCauseLink).toBeVisible();
      await expect(
        operateProcessInstancePage.getIncidentErrorMessageByText(
          'Called element error.',
        ),
      ).toBeVisible();
      await operateProcessInstancePage.clickOnElementInDiagram(
        'Task_CallActivity',
      );
      await operateProcessInstancePage.clickOnRootCauseProcessName();
      await operateProcessInstancePage.viewParentInstanceLink.click();
      await expect(operateProcessInstancePage.metadataPopover).toBeHidden({
        timeout: 10000,
      });
    });

    await test.step('Verify error indicators on all affected elements', async () => {
      // Wait for diagram to load completely
      await expect(operateProcessInstancePage.diagram).toBeVisible();
      await expect(operateProcessInstancePage.diagramSpinner).toBeHidden({
        timeout: 30000,
      });

      await expect(operateProcessInstancePage.incidentsBanner).toBeVisible({
        timeout: 10000,
      });
      // Click incidents banner to open incidents view
      await operateProcessInstancePage.clickIncidentsBanner();

      // Wait for the incident count heading to appear
      await expect(operateProcessInstancePage.incidentsViewHeader).toBeVisible({
        timeout: 10000,
      });

      // Get and verify the incident count from the heading
      const incidentCount = await operateProcessInstancePage.getIncidentCount();
      expect(incidentCount).toBeGreaterThan(0);

      // Verify the count matches expected value
      await operateProcessInstancePage.verifyIncidentCount(4);

      // Check if panel is visible before closing
      await operateProcessInstancePage.incidentsViewHeader.isVisible();
      await operateProcessInstancePage.clickIncidentsBanner();
    });
  });
});
