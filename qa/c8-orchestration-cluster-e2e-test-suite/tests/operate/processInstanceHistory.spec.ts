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
import {waitForAssertion} from 'utils/waitForAssertion';

type ProcessInstance = {processInstanceKey: number};

let incidentProcessInstance: ProcessInstance;

test.beforeAll(async () => {
  await deploy([
    './resources/IncidentProcess.bpmn',
    './resources/EmbeddedSubprocess.bpmn',
  ]);

  incidentProcessInstance = {
    processInstanceKey: Number(
      (await createSingleInstance('IncidentProcess', 1)).processInstanceKey,
    ),
  };

  await createSingleInstance('Process_EmbeddedSubprocess', 1);
});

test.describe('Process Instance History', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Verify Instance History', async ({
    page,
    operateProcessesPage,
    // operateInstancePage,
    operateFiltersPanelPage,
    operateProcessInstancePage,
  }) => {
    const incidentProcessInstanceKey =
      incidentProcessInstance.processInstanceKey;

    await test.step('Open Process Instances Page', async () => {
      await operateFiltersPanelPage.selectProcess('IncidentProcess');
      await operateFiltersPanelPage.selectVersion('1');
      await operateProcessesPage.clickProcessInstanceLink();
      const key = await operateProcessInstancePage.getProcessInstanceKey();
      expect(key).toContain(`${incidentProcessInstanceKey}`);
    });

    await test.step('Verify Instance History Tab has incidents', async () => {
      await expect(operateProcessInstancePage.instanceHistory).toBeVisible();
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.incidentsBanner,
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateProcessInstancePage.incidentsBanner).toContainText(
        '1 Incident',
      );

      const incidentIconsCount =
        await operateProcessInstancePage.getAllIncidentIconsAmountInHistory();
      expect(incidentIconsCount).toBe(2);
    });

    await test.step('Add variable to the process', async () => {
      await operateProcessInstancePage.clickAddVariableButton();
      await operateProcessInstancePage.fillNewVariable('goUp', '6');
      await operateProcessInstancePage.clickSaveVariableButton();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden();
    });

    await test.step('Retry incident', async () => {
      await operateProcessInstancePage.clickIncidentsBanner();
      const errorMessage =
        "Expected result of the expression 'goUp < 0' to be 'BOOLEAN'...";
      await operateProcessInstancePage.retryIncidentByErrorMessage(
        errorMessage,
      );
    });

    await test.step('Verify incident is resolved in Instance History', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessInstancePage.incidentsBanner).toBeHidden();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await waitForAssertion({
        assertion: async () => {
          const incidentIconsCountAfterResolution =
            await operateProcessInstancePage.getAllIncidentIconsAmountInHistory();
          expect(incidentIconsCountAfterResolution).toBe(0);
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });
  });
});
