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

  test('Edit JSON variable with inline editor', async ({
    page,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
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

    await test.step('Add a JSON variable', async () => {
      await operateProcessInstancePage.clickAddVariableButton();
      await operateProcessInstancePage.fillNewVariable(
        'jsonPayload',
        '{"status":"active","count":42}',
      );
      await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled();
      await operateProcessInstancePage.clickSaveVariableButton();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });
    });

    await test.step('Edit JSON variable inline and verify changes persist', async () => {
      const variable = operateProcessInstancePage.existingVariableByName(
        'jsonPayload',
      );

      await variable.editVariableModal.button.click();
      await variable.editVariableModal.valueInputField.waitFor({state: 'visible'});
      await variable.editVariableModal.valueInputField.fill(
        '{"status":"updated","count":100}',
      );
      await variable.editVariableModal.saveButton.click();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });

      await page.reload();
      await expect(
        page.getByText('"status":"updated","count":100', {exact: false}),
      ).toBeVisible();
    });

    await test.step('Verify inline editor shows validation error for invalid JSON', async () => {
      const variable = operateProcessInstancePage.existingVariableByName(
        'jsonPayload',
      );

      await variable.editVariableModal.button.click();
      await variable.editVariableModal.valueInputField.waitFor({state: 'visible'});
      await variable.editVariableModal.valueInputField.clear();
      await variable.editVariableModal.valueInputField.fill('{invalid json');

      await operateProcessInstancePage.checkExistingVariableErrorMessageText(
        'jsonPayload',
        'Value has to be JSON',
      );
      await expect(variable.editVariableModal.saveButton).toBeDisabled();

      await variable.editVariableModal.exitButton.click();
    });
  });

  test('Edit JSON variable with modal editor', async ({
    page,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
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

    await test.step('Add a JSON variable', async () => {
      await operateProcessInstancePage.clickAddVariableButton();
      await operateProcessInstancePage.fillNewVariable(
        'modalTestVar',
        '{"data":"initial"}',
      );
      await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled();
      await operateProcessInstancePage.clickSaveVariableButton();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });
    });

    await test.step('Open JSON editor modal and edit variable', async () => {
      const variable = operateProcessInstancePage.existingVariableByName(
        'modalTestVar',
      );

      await variable.editVariableModal.button.click();
      await variable.editVariableModal.valueInputField.waitFor({state: 'visible'});

      await variable.editVariableModal.jsonEditorButton.click();
      await expect(variable.editVariableModal.jsonEditorModal.header).toBeVisible();

      await variable.editVariableModal.jsonEditorModal.inputField.fill(
        '{"data":"modal-edited","verified":true}',
      );
      await variable.editVariableModal.jsonEditorModal.applyButton.click();

      await variable.editVariableModal.saveButton.click();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });

      await page.reload();
      await expect(
        page.getByText('"data":"modal-edited"', {exact: false}),
      ).toBeVisible();
    });

    await test.step('Verify JSON editor modal can cancel changes', async () => {
      const variable = operateProcessInstancePage.existingVariableByName(
        'modalTestVar',
      );

      await variable.editVariableModal.button.click();
      await variable.editVariableModal.valueInputField.waitFor({state: 'visible'});

      await variable.editVariableModal.jsonEditorButton.click();
      await expect(variable.editVariableModal.jsonEditorModal.header).toBeVisible();

      await variable.editVariableModal.jsonEditorModal.inputField.fill(
        '{"data":"should-be-cancelled"}',
      );
      await variable.editVariableModal.jsonEditorModal.cancelButton.click();

      await expect(
        variable.editVariableModal.valueInputField,
      ).toHaveValue('"data":"modal-edited","verified":true');

      await variable.editVariableModal.exitButton.click();
    });
  });

  test('View JSON variable in modal (read-only)', async ({
    page,
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    await test.step('Navigate to a completed process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.filterByProcessName(
        'simple service task process',
      );
      await operateProcessesPage.selectVersionFilter('All');
      await operateProcessesPage.selectRunningFilter('Finished');
      await sleep(500);
      await operateProcessesPage.clickProcessInstanceLink();
    });

    await test.step('Verify JSON variable can be viewed in modal (read-only)', async () => {
      const firstVariableName = await page
        .getByTestId('variables-list')
        .getByRole('row')
        .nth(1)
        .getByRole('cell')
        .nth(0)
        .textContent();

      if (!firstVariableName) {
        test.skip('No variables found in completed instance');
      }

      const variable = operateProcessInstancePage.existingVariableByName(
        firstVariableName,
      );

      const openButton = variable.value.getByRole('button', {
        name: /^open/i,
      });

      if (await openButton.isVisible()) {
        await openButton.click();
        await expect(
          page.getByRole('dialog').getByRole('textbox'),
        ).toBeVisible();

        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();

        await dialog.getByRole('button', {name: /close|cancel/i}).click();
        await expect(dialog).not.toBeVisible();
      }
    });
  });
});
