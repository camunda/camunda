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
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {
  createEligibilityIds,
  deployEligibilityForm,
  deployEligibilityProcess,
  deployTaggedDecision,
  searchDecisionInstancesByProcessInstanceKey,
  startEligibilityInstance,
  VIP_INPUT,
} from '@requestHelpers';

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

  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
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

  /**
   * Operate must show the version a FEEL version tag expression resolved to
   * (product-hub#3501). Asks for the *older* tag while a newer one is deployed,
   * so a latest-version fallback would fail this.
   */
  test('Decision version resolved by a FEEL version tag expression is shown in Operate', async ({
    page,
    request,
    operateDecisionInstancePage,
  }) => {
    const ids = createEligibilityIds('operateUi');
    let processInstanceKey: string;
    let expectedVersion: number;
    let decisionEvaluationInstanceKey: string;

    let latestVersion: number;

    await test.step('Deploy two tagged decision versions and a process binding by FEEL expression', async () => {
      await deployEligibilityForm();
      const v1 = await deployTaggedDecision(ids, 'v1', true);
      const v2 = await deployTaggedDecision(ids, 'v2', false);
      expectedVersion = v1.version;
      latestVersion = v2.version;
      expect(
        latestVersion,
        'v2 must be the newer deployment for this test to prove tag resolution',
      ).toBeGreaterThan(expectedVersion);
      await deployEligibilityProcess(ids, '= decisionVersion');
    });

    await test.step('Start an instance that requests the older version tag v1', async () => {
      processInstanceKey = await startEligibilityInstance(ids, {
        ...VIP_INPUT,
        decisionVersion: 'v1',
      });
      const [decisionInstance] =
        await searchDecisionInstancesByProcessInstanceKey(
          processInstanceKey,
          request,
        );
      decisionEvaluationInstanceKey =
        decisionInstance.decisionEvaluationInstanceKey;
    });

    await test.step('Open the decision instance in Operate', async () => {
      await navigateToAppHome(page, 'operate');
      await operateDecisionInstancePage.gotoDecisionInstancePage({
        id: decisionEvaluationInstanceKey,
      });
      await expect(operateDecisionInstancePage.instanceHeader).toBeVisible();
    });

    await test.step('Header shows the version the expression resolved to, not the latest', async () => {
      await expect(
        operateDecisionInstancePage.instanceHeader.getByRole('link', {
          name: `View decision "Decide Upgrade Eligibility version ${expectedVersion}" instances`,
        }),
      ).toBeVisible();
      await expect(
        operateDecisionInstancePage.instanceHeader.getByRole('link', {
          name: `View decision "Decide Upgrade Eligibility version ${latestVersion}" instances`,
        }),
      ).toBeHidden();
      await expect(
        operateDecisionInstancePage.instanceHeader.getByRole('link', {
          name: `View process instance ${processInstanceKey}`,
        }),
      ).toBeVisible();
    });

    await test.step('Evaluated decision table is rendered', async () => {
      await expect(operateDecisionInstancePage.decisionPanel).toBeVisible();
      await expect(
        operateDecisionInstancePage.decisionPanel.getByText('User Status'),
      ).toBeVisible();
    });
  });
});
