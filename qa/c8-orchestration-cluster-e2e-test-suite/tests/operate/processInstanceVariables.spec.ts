/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {
  deploy,
  createInstances,
  generateManyVariables,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {expectInViewport} from 'utils/expectInViewport';
import {sleep} from 'utils/sleep';

test.beforeAll(async () => {
  await deploy([
    './resources/variableScrollingProcess.bpmn',
    './resources/simpleServiceTaskProcess.bpmn',
  ]);
  const manyVariables = generateManyVariables();
  await createInstances('variableScrollingProcess', 1, 1, manyVariables);
  await createInstances('simpleServiceTaskProcess', 1, 1);
});

test.describe('Process Instance Variables', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Edit variables', async ({
    page,
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
  }) => {
    test.slow();

    await test.step('Navigate to Processes tab and open the process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName(
        'variable scrolling process',
      );
      await sleep(100);
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.addVariableButton).toBeEnabled();
    });

    await test.step('Click edit variable button and verify Save Variable button is enabled', async () => {
      await operateProcessInstancePage.clickEditVariableButton('aa');
      await operateProcessInstancePage.clickVariableValueInput();
      await operateProcessInstancePage.clearVariableValueInput();
      await operateProcessInstancePage.fillVariableValueInput(
        '"editedTestValue"',
      );
      await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled({
        timeout: 30000,
      });
    });

    await test.step('Click Save Variable button and verify that both edit variable spinner and operation spinner are displayed', async () => {
      await operateProcessInstancePage.saveVariableButton.click();
      await expect(operateProcessInstancePage.variableSpinner).toBeVisible();
    });

    await test.step('Verify that both spinners disappear after saving the variable', async () => {
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });
      await expect(operateProcessInstancePage.operationSpinner).toBeHidden({
        timeout: 60000,
      });
    });

    await test.step('Refresh the page and verify the variable is still there', async () => {
      await page.reload();
      await expect(page.getByText('editedtestvalue')).toBeVisible();
    });
  });

  test('Add variables', async ({
    page,
    operateProcessInstancePage,
    operateHomePage,
    operateFiltersPanelPage,
    operateProcessesPage,
  }) => {
    test.slow();

    await test.step('Navigate to Processes tab and open the process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName(
        'simple service task process',
      );
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.addVariableButton).toBeEnabled();
    });

    await test.step('Add a new variable', async () => {
      await operateProcessInstancePage.clickAddVariableButton();
      await operateProcessInstancePage.fillNewVariable(
        'secondTestKey',
        '"secondTestValue"',
      );
      await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled();
    });

    await test.step('Click Save Variable button and verify that variable spinner and toast are displayed', async () => {
      await operateProcessInstancePage.clickSaveVariableButton();
      await expect(operateProcessInstancePage.variableSpinner).toBeVisible();
      await expect(operateProcessInstancePage.variableAddedBanner).toBeVisible({
        timeout: 60000,
      });
    });

    await test.step('Verify that the spinner disappears after saving the variable', async () => {
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });
    });

    await test.step('Refresh the page and verify the variable is still there', async () => {
      await page.reload();
      await expect(
        page.getByText('secondTestKey', {exact: true}),
      ).toBeVisible();
      await expect(page.getByText('"secondTestValue"')).toBeVisible();
    });

    await test.step('Get the process instance key, navigate to Instances tab, and search using the added variable', async () => {
      const processInstanceKey =
        await operateProcessInstancePage.getProcessInstanceKey();

      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.displayOptionalFilter('Variable');
      await operateFiltersPanelPage.fillVariableNameFilter('secondTestKey');
      await operateFiltersPanelPage.fillVariableValueFilter(
        '"secondTestValue"',
      );
      await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
        processInstanceKey,
      );

      await expect(page.getByText('1 result')).toBeVisible();
      await operateProcessesPage.assertProcessInstanceLink(processInstanceKey);
    });
  });

  test('Infinite scrolling', async ({
    page,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
    operateFiltersPanelPage,
  }) => {
    await test.step('Navigate to Processes tab and open the process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess('variable scrolling process');
      await operateProcessesPage.clickProcessInstanceLink();
    });

    await test.step('Scroll through variables and verify row count increases progressively', async () => {
      await expect(operateProcessInstancePage.variablesList).toBeVisible();
      await expect(
        operateProcessInstancePage.variablesList.getByRole('row'),
      ).toHaveCount(51);

      await expect(page.getByText('aa', {exact: true})).toBeVisible();
      await expect(page.getByText('bx', {exact: true})).toBeVisible();

      await page.getByText('bx', {exact: true}).scrollIntoViewIfNeeded();
      await expect(
        operateProcessInstancePage.variablesList.getByRole('row'),
      ).toHaveCount(101);

      await expect(page.getByText('dv', {exact: true})).toBeVisible();
      await page.getByText('dv', {exact: true}).scrollIntoViewIfNeeded();
      await expect(
        operateProcessInstancePage.variablesList.getByRole('row'),
      ).toHaveCount(151);

      await expect(page.getByText('ft', {exact: true})).toBeVisible();
      await page.getByText('ft', {exact: true}).scrollIntoViewIfNeeded();
      await expect(
        operateProcessInstancePage.variablesList.getByRole('row'),
      ).toHaveCount(201);

      await expect(page.getByText('hr', {exact: true})).toBeVisible();
      await page.getByText('hr', {exact: true}).scrollIntoViewIfNeeded();

      await expectInViewport(page.getByTestId('variable-aa'), false);
      await expect(page.getByText('by', {exact: true})).toBeVisible();
      await expect(page.getByText('jp', {exact: true})).toBeVisible();

      await page.getByText('by', {exact: true}).scrollIntoViewIfNeeded();
      await expect(
        operateProcessInstancePage.variablesList.getByRole('row'),
      ).toHaveCount(251);

      await expectInViewport(page.getByTestId('variable-jp'), false);
      await expect(page.getByText('aa', {exact: true})).toBeVisible();
      await expect(page.getByText('by', {exact: true})).toBeVisible();
    });
  });
});
