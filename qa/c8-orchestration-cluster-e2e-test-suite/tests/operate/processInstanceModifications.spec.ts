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
import {navigateToApp, hideModificationHelperModal} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';

type ProcessInstance = {
  processInstanceKey: string;
};

let instanceWithoutAnIncident: ProcessInstance;

test.beforeAll(async () => {
  await deploy(['./resources/withoutIncidentsProcess_v_1.bpmn']);

  instanceWithoutAnIncident = await createSingleInstance(
    'withoutIncidentsProcess',
    1,
    {
      test: 123,
      foo: 'bar',
    },
  );

  await sleep(2000);
});

test.describe('Process Instance Modifications', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await hideModificationHelperModal(page);
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Should apply/remove edit variable modifications', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('Navigate to process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: instanceWithoutAnIncident.processInstanceKey,
      });
    });

    await test.step('Verify initial variables are visible', async () => {
      await expect(
        operateProcessInstancePage.getVariableTestId('foo'),
      ).toBeVisible();
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();

      await expect(
        operateProcessInstancePage.modificationModeText,
      ).toBeVisible();
    });

    await test.step('Edit variable foo to value 1', async () => {
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '1',
      );

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getEditVariableModificationText('foo'),
      ).toBeVisible();
    });

    await test.step('Edit variable test to value 2', async () => {
      await operateProcessInstancePage.editVariableValueModificationMode(
        'test',
        '2',
      );

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getEditVariableModificationText('test'),
      ).toBeVisible();
    });

    await test.step('Edit variable foo again to value 3', async () => {
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '3',
      );

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getEditVariableModificationText('foo'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('3');
    });

    await test.step('Undo last modification and verify value reverted to 1', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('1');
      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getEditVariableModificationText('test'),
      ).toBeVisible();
    });

    await test.step('Navigate to different flow node and undo', async () => {
      await operateProcessInstancePage.clickFlowNode('never fails');
      await expect(operateProcessInstancePage.noVariablesText).toBeVisible();

      await operateProcessInstancePage.undoModification();
      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getEditVariableModificationText('foo'),
      ).toBeVisible();
    });

    await test.step('Return to root scope and verify correct values', async () => {
      await operateProcessInstancePage.navigateToRootScope();

      await expect(
        operateProcessInstancePage.getVariableTestId('foo'),
      ).toBeVisible();

      // Skipped due to bug 42546: https://github.com/camunda/camunda/issues/42546
      // await expect(
      //   operateProcessInstancePage.getEditVariableFieldSelector('test'),
      // ).toHaveValue('123');
      // await expect(
      //   operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      // ).toHaveValue('1');
    });

    await test.step('Undo again and verify all modifications removed', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('test'),
      ).toHaveValue('123');
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('"bar"');
    });

    await test.step('Edit variable and remove from summary modal', async () => {
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '1',
      );
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '2',
      );

      await operateProcessInstancePage.clickApplyModifications();

      await expect(
        operateProcessInstancePage.getVariableModificationSummaryText(
          'foo',
          '2',
        ),
      ).toBeVisible();
      await operateProcessInstancePage.clickDeleteVariableModification();

      await operateProcessInstancePage.clickCancel();

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('"bar"');
    });
  });

  // Skipped due to bug 42546: https://github.com/camunda/camunda/issues/42546
  // !Note: assert the code after the bug is fixed as it was discoverd during the test implementation
  // eslint-disable-next-line playwright/no-skipped-test
  test.skip('Should apply/remove add variable modifications', async ({
    page,
    operateProcessInstancePage,
  }) => {
    await test.step('Navigate to process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: instanceWithoutAnIncident.processInstanceKey,
      });
      await sleep(1000);
    });

    await test.step('Verify initial variables are visible', async () => {
      await expect(
        operateProcessInstancePage.getVariableTestId('foo'),
      ).toBeVisible();
    });

    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.enterModificationMode();

      await expect(
        operateProcessInstancePage.modificationModeText,
      ).toBeVisible();
    });

    await test.step('Add first new variable test2', async () => {
      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test2',
        '1',
      );

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getAddVariableModificationText('test2'),
      ).toBeVisible();
    });

    await test.step('Add second new variable test3', async () => {
      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[1]',
        'test3',
        '2',
      );

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getAddVariableModificationText('test3'),
      ).toBeVisible();
    });

    await test.step('Edit first added variable name', async () => {
      await operateProcessInstancePage
        .getNewVariableNameFieldSelector('newVariables[0]')
        .clear();
      await operateProcessInstancePage
        .getNewVariableNameFieldSelector('newVariables[0]')
        .type('test2-edited');
      await page.keyboard.press('Tab');

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getAddVariableModificationText(
          'test2-edited',
        ),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getNewVariableNameFieldSelector(
          'newVariables[0]',
        ),
      ).toHaveValue('test2-edited');
      await expect(
        operateProcessInstancePage.getNewVariableNameFieldSelector(
          'newVariables[1]',
        ),
      ).toHaveValue('test3');
    });

    await test.step('Undo last modification and verify name reverted', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.getNewVariableNameFieldSelector(
          'newVariables[0]',
        ),
      ).toHaveValue('test2');
      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getAddVariableModificationText('test3'),
      ).toBeVisible();
    });

    await test.step('Navigate to different flow node and undo', async () => {
      await operateProcessInstancePage.clickFlowNode('never fails');
      await expect(operateProcessInstancePage.noVariablesText).toBeVisible();

      await operateProcessInstancePage.undoModification();
      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getAddVariableModificationText('test2'),
      ).toBeVisible();
    });

    await test.step('Return to root scope and verify first variable', async () => {
      await operateProcessInstancePage.navigateToRootScope();

      await expect(
        operateProcessInstancePage.getVariableTestId('newVariables[0]'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getVariableTestId('newVariables[1]'),
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getNewVariableNameFieldSelector(
          'newVariables[0]',
        ),
      ).toHaveValue('test2');
      await expect(
        operateProcessInstancePage.getNewVariableValueFieldSelector(
          'newVariables[0]',
        ),
      ).toHaveValue('1');
    });

    await test.step('Undo again and verify new variable field removed', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getVariableTestId('newVariables[0]'),
      ).toBeHidden();
    });

    await test.step('Add variables to different scopes with same name', async () => {
      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test3',
        '1',
      );

      await operateProcessInstancePage.clickFlowNode('never fails');
      await expect(
        page.getByText(/The Flow Node has no Variables/i),
      ).toBeVisible();

      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test3',
        '1',
      );
    });

    await test.step('Remove one variable from summary modal', async () => {
      await operateProcessInstancePage.clickApplyModifications();

      await expect(
        operateProcessInstancePage.getDialogVariableModificationSummaryText(
          'test3',
          '1',
        ),
      ).toHaveCount(2);

      await operateProcessInstancePage.clickDialogDeleteVariableModification(1);

      await operateProcessInstancePage.clickDialogCancel();

      await expect(
        operateProcessInstancePage.getVariableTestId('newVariables[0]'),
      ).toBeHidden();
    });

    await test.step('Verify first scope still has the variable', async () => {
      await operateProcessInstancePage.navigateToRootScope();

      await expect(
        operateProcessInstancePage.getVariableTestId('newVariables[0]'),
      ).toBeVisible();
    });

    await test.step('Change new variable value and remove from summary', async () => {
      await operateProcessInstancePage
        .getNewVariableValueFieldSelector('newVariables[0]')
        .type('2');
      await page.keyboard.press('Tab');

      await operateProcessInstancePage.clickApplyModifications();

      await expect(
        operateProcessInstancePage.getVariableModificationSummaryText(
          'test3',
          '21',
        ),
      ).toBeVisible();

      await operateProcessInstancePage.clickDeleteVariableModification();

      await operateProcessInstancePage.clickCancel();

      await expect(
        operateProcessInstancePage.lastAddedModificationText,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getVariableTestId('newVariables[0]'),
      ).toBeHidden();
    });
  });
});
