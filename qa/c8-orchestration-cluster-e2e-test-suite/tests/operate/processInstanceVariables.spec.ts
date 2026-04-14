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
  createSingleInstance,
  generateManyVariables,
  createWorker,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {expectInViewport} from 'utils/expectInViewport';
import {sleep} from 'utils/sleep';

const JSON_VARIABLE_NAME = 'jsonVar';
const JSON_VARIABLE_VALUE = {name: 'Alice', age: 30};

let completedInstanceKey: string;

test.beforeAll(async () => {
  await deploy([
    './resources/variableScrollingProcess.bpmn',
    './resources/simpleServiceTaskProcess.bpmn',
    './resources/jsonVariableProcess.bpmn',
  ]);
  const manyVariables = generateManyVariables();
  await createInstances('variableScrollingProcess', 1, 1, manyVariables);
  await createInstances('simpleServiceTaskProcess', 1, 1);

  const worker = createWorker('jsonVarTask', false);

  const completedInstance = await createSingleInstance(
    'jsonVariableProcess',
    1,
    {[JSON_VARIABLE_NAME]: JSON_VARIABLE_VALUE},
  );
  completedInstanceKey = String(completedInstance.processInstanceKey);

  // Wait for the worker to process and complete the job
  await sleep(1000);
  await worker.close();

  await createInstances('jsonVariableProcess', 1, 1, {
    [JSON_VARIABLE_NAME]: JSON_VARIABLE_VALUE,
  });
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

  test('Open JSON viewer via maximize action on running instance', async ({
    page,
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    await test.step('Navigate to the running json variable process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess('json variable process');
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.variablesList).toBeVisible();
    });

    await test.step('Verify the maximize button is visible in the actions column', async () => {
      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorButton,
      ).toBeVisible();
    });

    await test.step('Click the maximize button and verify the JSON viewer modal opens in read-only mode', async () => {
      await operateProcessInstancePage
        .existingVariableByName(JSON_VARIABLE_NAME)
        .editVariableModal.jsonEditorButton.click();
      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorModal.header,
      ).toBeVisible({timeout: 10000});
      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorModal.header,
      ).toContainText(JSON_VARIABLE_NAME);

      await expect(
        page.getByRole('dialog').getByRole('button', {name: 'Apply'}),
      ).toBeHidden();

      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorModal.copyButton,
      ).toBeVisible();
    });

    await test.step('Close the modal', async () => {
      await page
        .getByRole('dialog')
        .getByRole('button', {name: 'Close'})
        .click();
      await expect(page.getByRole('dialog')).toBeHidden();
    });
  });

  test('Edit JSON via modal Apply saves variable without extra save', async ({
    page,
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    test.slow();

    await test.step('Navigate to the running json variable process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess('json variable process');
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.addVariableButton).toBeEnabled();
    });

    await test.step('Enter edit mode for the JSON variable', async () => {
      await operateProcessInstancePage.clickEditVariableButton(
        JSON_VARIABLE_NAME,
      );
    });

    await test.step('Open the JSON editor modal via the maximize button', async () => {
      await operateProcessInstancePage
        .existingVariableByName(JSON_VARIABLE_NAME)
        .editVariableModal.jsonEditorButton.click();
      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorModal.header,
      ).toBeVisible({timeout: 10000});
      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorModal.header,
      ).toContainText(JSON_VARIABLE_NAME);
    });

    await test.step('Edit the JSON value in the modal and click Apply', async () => {
      const inputField = page.getByRole('dialog').getByRole('code');
      await expect(inputField).toBeVisible({timeout: 10000});

      // Select all existing content and replace with updated JSON
      await inputField.click();
      await page.keyboard.press('Control+A');
      await page.keyboard.press('Backspace');
      await page.keyboard.type('{"name":"Bob","age":25}');

      await page
        .getByRole('dialog')
        .getByRole('button', {name: 'Apply'})
        .click();
    });

    await test.step('Verify variable is saved without an extra Save click (spinners disappear)', async () => {
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });
      await expect(operateProcessInstancePage.operationSpinner).toBeHidden({
        timeout: 60000,
      });
    });

    await test.step('Reload and verify the updated value is persisted', async () => {
      await page.reload();
      await expect(
        operateProcessInstancePage.variablesList.getByTestId(
          `variable-${JSON_VARIABLE_NAME}`,
        ),
      ).toContainText('Bob');
    });
  });

  test('Inline JSON is pretty-printed in view mode', async ({
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    await test.step('Navigate to the running json variable process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess('json variable process');
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.variablesList).toBeVisible();
    });

    await test.step('Verify the JSON variable value is pretty-printed inline', async () => {
      const valueCell = operateProcessInstancePage.variablesList
        .getByTestId(`variable-${JSON_VARIABLE_NAME}`)
        .getByRole('cell')
        .nth(1);

      await expect(valueCell).toBeVisible();

      // Pretty-printed JSON shows each key on its own line
      await expect(valueCell).toContainText('"name"');
      await expect(valueCell).toContainText('"age"');

      // Verify the content is not collapsed on a single line
      const text = await valueCell.innerText();
      expect(text).toMatch(/"name"/);
      expect(text).toMatch(/"age"/);
      expect(text.split('\n').length).toBeGreaterThan(1);
    });
  });

  test('Copy button works in JSON viewer for running instance', async ({
    page,
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    await test.step('Grant clipboard permissions', async () => {
      await page
        .context()
        .grantPermissions(['clipboard-read', 'clipboard-write']);
    });

    await test.step('Navigate to the running json variable process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess('json variable process');
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.variablesList).toBeVisible();
    });

    await test.step('Open the JSON viewer modal', async () => {
      await operateProcessInstancePage
        .existingVariableByName(JSON_VARIABLE_NAME)
        .editVariableModal.jsonEditorButton.click();
      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorModal.header,
      ).toBeVisible({timeout: 10000});
    });

    await test.step('Click Copy and verify the button shows Copied feedback', async () => {
      await operateProcessInstancePage
        .existingVariableByName(JSON_VARIABLE_NAME)
        .editVariableModal.jsonEditorModal.copyButton.click();
      await expect(
        page.getByRole('dialog').getByRole('button', {name: /copied/i}),
      ).toBeVisible({timeout: 10000});
    });

    await test.step('Verify clipboard contains the full JSON value', async () => {
      const clipboardText = await page.evaluate(() =>
        navigator.clipboard.readText(),
      );
      expect(clipboardText).toContain('"name"');
      expect(clipboardText).toContain('"age"');
    });

    await test.step('Close the modal', async () => {
      await page
        .getByRole('dialog')
        .getByRole('button', {name: 'Close'})
        .click();
      await expect(page.getByRole('dialog')).toBeHidden();
    });
  });

  test('Copy button works in JSON viewer for completed instance', async ({
    page,
    operateProcessInstancePage,
  }) => {
    await test.step('Grant clipboard permissions', async () => {
      await page
        .context()
        .grantPermissions(['clipboard-read', 'clipboard-write']);
    });

    await test.step('Navigate directly to the completed json variable process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: completedInstanceKey,
      });
      await expect(operateProcessInstancePage.completedIcon).toBeVisible({
        timeout: 30000,
      });
      await expect(operateProcessInstancePage.variablesList).toBeVisible();
    });

    await test.step('Open the JSON viewer modal (read-only for completed instances)', async () => {
      await operateProcessInstancePage
        .existingVariableByName(JSON_VARIABLE_NAME)
        .editVariableModal.jsonEditorButton.click();
      await expect(
        operateProcessInstancePage.existingVariableByName(JSON_VARIABLE_NAME)
          .editVariableModal.jsonEditorModal.header,
      ).toBeVisible({
        timeout: 10000,
      });
      // Completed instance viewer is passive: no Apply button
      await expect(
        page.getByRole('dialog').getByRole('button', {name: 'Apply'}),
      ).toBeHidden();
    });

    await test.step('Click Copy and verify the button shows Copied feedback', async () => {
      await operateProcessInstancePage
        .existingVariableByName(JSON_VARIABLE_NAME)
        .editVariableModal.jsonEditorModal.copyButton.click();
      await expect(
        page.getByRole('dialog').getByRole('button', {name: /copied/i}),
      ).toBeVisible({timeout: 10000});
    });

    await test.step('Verify clipboard contains the full JSON value', async () => {
      const clipboardText = await page.evaluate(() =>
        navigator.clipboard.readText(),
      );
      expect(clipboardText).toContain('"name"');
      expect(clipboardText).toContain('"age"');
    });

    await test.step('Close the modal', async () => {
      await page
        .getByRole('dialog')
        .getByRole('button', {name: 'Close'})
        .click();
      await expect(page.getByRole('dialog')).toBeHidden();
    });
  });

  test('Inline JSON edit uses Monaco textarea', async ({
    page,
    operateProcessInstancePage,
    operateHomePage,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    test.slow();

    await test.step('Navigate to the running json variable process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess('json variable process');
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.addVariableButton).toBeEnabled();
    });

    await test.step('Enter edit mode for the JSON variable', async () => {
      await operateProcessInstancePage.clickEditVariableButton(
        JSON_VARIABLE_NAME,
      );
    });

    await test.step('Verify the inline editor is a Monaco textarea, not a plain text input', async () => {
      const valueCell = operateProcessInstancePage.variablesList
        .getByTestId(`variable-${JSON_VARIABLE_NAME}`)
        .getByRole('cell')
        .nth(1);

      const monacoEditor = valueCell.getByRole('code');
      await expect(monacoEditor).toBeVisible({timeout: 10000});
    });

    await test.step('Edit the inline value using Monaco and trigger save', async () => {
      const valueCell = operateProcessInstancePage.variablesList
        .getByTestId(`variable-${JSON_VARIABLE_NAME}`)
        .getByRole('cell')
        .nth(1);
      const monacoEditor = valueCell.getByRole('code');

      await monacoEditor.click();
      await page.keyboard.press('Control+A');
      await page.keyboard.press('Backspace');
      await page.keyboard.type('{"name":"Charlie","age":35}');
      await page.keyboard.press('Tab');
    });

    await test.step('Click Save and verify the variable is persisted', async () => {
      await operateProcessInstancePage.clickSaveVariableButton();
      await expect(operateProcessInstancePage.variableSpinner).toBeHidden({
        timeout: 60000,
      });
      await expect(operateProcessInstancePage.operationSpinner).toBeHidden({
        timeout: 60000,
      });
    });

    await test.step('Reload and verify the updated value is visible', async () => {
      await page.reload();
      await expect(
        operateProcessInstancePage.variablesList.getByTestId(
          `variable-${JSON_VARIABLE_NAME}`,
        ),
      ).toContainText('Charlie');
    });
  });
});
