/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {deploy} from 'utils/zeebeClient';
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';

interface DecisionInfo {
  key: string;
  version: string;
}

let initialData: {
  decisions: DecisionInfo[];
};

test.describe('Decision Instances', () => {
  test.beforeAll(async ({}) => {
    // Deploy decision versions 1 and 2
    const decisionV1Response = await deploy(['./resources/decisions_v_1.dmn']);
    const decisionV2Response = await deploy(['./resources/decisions_v_2.dmn']);

    const decisions: DecisionInfo[] = [];

    // Process decisions from v1
    decisionV1Response.decisions?.forEach((decision) => {
      decisions.push({
        key: decision.decisionDefinitionKey,
        version: decision.version.toString(),
      });
    });

    // Process decisions from v2
    decisionV2Response.decisions?.forEach((decision) => {
      decisions.push({
        key: decision.decisionDefinitionKey,
        version: decision.version.toString(),
      });
    });

    initialData = {decisions};

    await sleep(2000);
  });

  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Switch between Decision versions', async ({
    operateDecisionsPage,
    operateHomePage,
  }) => {
    const {decisions} = initialData;
    const [
      decision1Version1,
      decision2Version1,
      decision1Version2,
      decision2Version2,
    ] = decisions;

    await test.step('Navigate to Decisions page', async () => {
      await operateHomePage.clickDecisionsTab();
    });

    await test.step('Select Decision 1 Version 1', async () => {
      await operateDecisionsPage.selectDecisionName('Decision 1');
      await operateDecisionsPage.selectVersion(decision1Version1!.version);
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Decision 1'),
      ).toBeVisible();
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Version 1'),
      ).toBeVisible();
    });

    await test.step('Switch to Decision 1 Version 2', async () => {
      await operateDecisionsPage.selectVersion(decision1Version2!.version);
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Decision 1'),
      ).toBeVisible();
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Version 2'),
      ).toBeVisible();
    });

    await test.step('Clear selection and select Decision 2', async () => {
      await operateDecisionsPage.clearComboBox();
      await operateDecisionsPage.selectDecisionName('Decision 2');
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Decision 2'),
      ).toBeVisible();
    });

    await test.step('Select Decision 2 Version 1', async () => {
      await operateDecisionsPage.selectVersion(decision2Version1!.version);
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Decision 2'),
      ).toBeVisible();
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Version 1'),
      ).toBeVisible();
    });

    await test.step('Switch to Decision 2 Version 2', async () => {
      await operateDecisionsPage.selectVersion(decision2Version2!.version);
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Decision 2'),
      ).toBeVisible();
      await expect(
        operateDecisionsPage.decisionViewer.getByText('Version 2'),
      ).toBeVisible();
    });
  });
});
