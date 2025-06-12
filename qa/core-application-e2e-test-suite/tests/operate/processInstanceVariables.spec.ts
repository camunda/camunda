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

test.beforeAll(async () => {
  await deploy([
    './resources/onlyIncidentsProcess_v_1.bpmn',
    './resources/onlyIncidentsProcess_scrolling.bpmn',
  ]);
  const manyVariables = generateManyVariables();
  await createInstances('onlyIncidentsProcessScrolling', 1, 1, manyVariables);
  await createInstances('onlyIncidentsProcess', 1, 1);
});

test.describe('Process Instance Variables', () => {
  test.beforeEach(async ({page, operateLoginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await operateLoginPage.login('demo', 'demo');
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

    await operateHomePage.clickProcessesTab();
    await operateProcessesPage.filterByProcessName(
      'Only Incidents Process Scrolling',
    );
    await operateProcessesPage.clickProcessInstanceLink();
    await expect(operateProcessInstancePage.addVariableButton).toBeEnabled();

    // open process instance page, after clicking the edit variable button see that save variable button is disabled.
    await operateProcessInstancePage.clickEditVariableButton('aa');
    await operateProcessInstancePage.clickVariableValueInput();
    await operateProcessInstancePage.clearVariableValueInput();
    await operateProcessInstancePage.fillVariableValueInput(
      '"editedTestValue"',
    );

    // click save variable button and see that both edit variable spinner and operation spinner are displayed.
    await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled({
      timeout: 30000,
    });
    await operateProcessInstancePage.saveVariableButton.click();
    await expect(operateProcessInstancePage.variableSpinner).toBeVisible();
    await expect(operateProcessInstancePage.operationSpinner).toBeVisible();

    // see that spinners both disappear after save variable operation completes.
    await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
      timeout: 60000,
    });
    await expect(operateProcessInstancePage.operationSpinner).toBeHidden({
      timeout: 60000,
    });

    // refresh the page and see the variable is still there.
    await page.reload();
    // await expect(page.getByText('editedtestvalue')).toBeVisible();
    await expect(page.getByText(/"\\"editedtestvalue\\""/i)).toBeVisible();
  });

  test('Add variables', async ({
    page,
    operateProcessInstancePage,
    operateHomePage,
    operateFiltersPanel,
    operateProcessesPage,
  }) => {
    test.slow();

    await operateHomePage.clickProcessesTab();
    await operateProcessesPage.filterByProcessName('Only Incidents Process');
    await operateProcessesPage.clickProcessInstanceLink();
    await expect(operateProcessInstancePage.addVariableButton).toBeEnabled();

    // add a new variable
    await operateProcessInstancePage.clickAddVariableButton();
    await operateProcessInstancePage.fillNewVariable(
      'secondTestKey',
      '"secondTestValue"',
    );

    await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled();

    // click save variable button and see that both variable spinner and operation spinner are displayed.
    await operateProcessInstancePage.clickSaveVariableButton();
    await expect(operateProcessInstancePage.variableSpinner).toBeVisible();
    await expect(operateProcessInstancePage.operationSpinner).toBeVisible();

    // see that spinners both disappear after save variable operation completes
    await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
      timeout: 60000,
    });
    await expect(operateProcessInstancePage.operationSpinner).toBeHidden({
      timeout: 60000,
    });

    // refresh the page and see the variable is still there.
    await page.reload();
    await expect(page.getByText('secondTestKey', {exact: true})).toBeVisible();
    // await expect(page.getByText('"secondTestValue"')).toBeVisible();
    await expect(page.getByText(/"\\"secondTestValue\\""/i)).toBeVisible();

    // get process instance key
    const processInstanceKey =
      await operateProcessInstancePage.getProcessInstanceKey();

    // go to instance page, filter and find the instance by added variable
    await operateHomePage.clickProcessesTab();

    await operateFiltersPanel.displayOptionalFilter('Process Instance Key(s)');
    await operateFiltersPanel.displayOptionalFilter('Variable');

    await operateFiltersPanel.fillVariableNameFilter('secondTestKey');

    await operateFiltersPanel.fillVariableValueFilter(
      '"\\\"secondTestValue\\\""',
    );
    // await operateFiltersPanel.fillVariableValueFilter('"secondTestValue"');
    await operateFiltersPanel.fillProcessInstanceKeyFilter(processInstanceKey);

    await expect(page.getByText('1 result')).toBeVisible();

    await operateProcessesPage.assertProcessInstanceLink(processInstanceKey);
  });

  test('Infinite scrolling', async ({
    page,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    await operateHomePage.clickProcessesTab();
    await operateProcessesPage.filterByProcessName(
      'Only Incidents Process Scrolling',
    );
    await operateProcessesPage.clickProcessInstanceLink();

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

    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('dv', {exact: true})).toBeVisible();

    await page.getByText('dv', {exact: true}).scrollIntoViewIfNeeded();

    await expect(
      operateProcessInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(151);

    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('ft', {exact: true})).toBeVisible();

    await page.getByText('ft', {exact: true}).scrollIntoViewIfNeeded();

    await expect(
      operateProcessInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(201);

    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('hr', {exact: true})).toBeVisible();

    await page.getByText('hr', {exact: true}).scrollIntoViewIfNeeded();

    await expect(
      operateProcessInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(201);

    await expect(page.getByText('aa', {exact: true})).toBeHidden();
    await expect(page.getByText('by', {exact: true})).toBeVisible();
    await expect(page.getByText('jp', {exact: true})).toBeVisible();

    await page.getByText('by', {exact: true}).scrollIntoViewIfNeeded();
    await expect(
      operateProcessInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(201);

    await expect(page.getByText('jp', {exact: true})).toBeHidden();
    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('by', {exact: true})).toBeVisible();
  });
});
