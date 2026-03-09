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
import {waitForProcessInstances} from 'utils/incidentsHelper';

type ProcessInstance = {
  processInstanceKey: string;
};

let instanceWithoutAnIncident: ProcessInstance;

test.beforeAll(async ({request}) => {
  await deploy(['./resources/processInstanceModifications_v_1.bpmn']);

  instanceWithoutAnIncident = await createSingleInstance(
    'processInstanceModifications',
    1,
    {
      test: 123,
      foo: 'bar',
    },
  );

  await waitForProcessInstances(
    request,
    [instanceWithoutAnIncident.processInstanceKey],
    1,
  );
});

test.describe('Modifications', () => {
  test.beforeEach(
    async ({page, loginPage, operateHomePage, operateProcessInstancePage}) => {
      await hideModificationHelperModal(page);
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.operateBanner).toBeVisible();
      await operateProcessInstancePage.navigateToProcessInstance(
        instanceWithoutAnIncident.processInstanceKey,
      );
    },
  );

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Should apply/remove edit variable modifications', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.assertAndEnterModificationMode();
    });

    await test.step('Edit variable "foo" to 1', async () => {
      await operateProcessInstancePage.editExistingVariable('foo', '1');
      await operateProcessInstancePage.assertLastModificationIs(
        /edit variable "foo"/i,
      );
    });

    await test.step('Edit variable "test" to 2', async () => {
      await operateProcessInstancePage.editExistingVariable('test', '2');
      await operateProcessInstancePage.assertLastModificationIs(
        /edit variable "test"/i,
      );
    });

    await test.step('Edit variable "foo" to 3', async () => {
      await operateProcessInstancePage.editExistingVariable('foo', '3');
      await operateProcessInstancePage.assertLastModificationIs(
        /edit variable "foo"/i,
      );
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('3');
    });

    await test.step('Undo last edit – value reverts to 1', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('1');
      await operateProcessInstancePage.assertLastModificationIs(
        /edit variable "test"/i,
      );
    });

    await test.step('Navigate to another flow node and undo from there', async () => {
      await operateProcessInstancePage.instanceHistory
        .getByText(/never fails/i)
        .click();
      await expect(operateProcessInstancePage.noVariablesText).toBeVisible();

      await operateProcessInstancePage.undoModification();
      await operateProcessInstancePage.assertLastModificationIs(
        /edit variable "foo"/i,
      );

      await operateProcessInstancePage.instanceHistory
        .getByText(/process instance modifications/i)
        .click();

      await expect(
        operateProcessInstancePage.getVariableRow('foo'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('test'),
      ).toHaveValue('123');
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('1');
    });

    await test.step('Undo again – footer disappears and all values revert', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.lastModificationFooter,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('test'),
      ).toHaveValue('123');
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('"bar"');
    });

    await test.step('Remove modification from summary modal', async () => {
      await operateProcessInstancePage.editExistingVariable('foo', '1');
      await operateProcessInstancePage.editExistingVariable('foo', '2');
      await operateProcessInstancePage.clickApplyModificationsButton();

      await expect(
        operateProcessInstancePage.getModificationSummaryItem('foo: 2'),
      ).toBeVisible();

      await operateProcessInstancePage.clickDeleteVariableModification();
      await operateProcessInstancePage.cancelModificationSummary();

      await expect(
        operateProcessInstancePage.lastModificationFooter,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveValue('"bar"');
    });
  });

  test('Should apply/remove add variable modifications', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('Enter modification mode', async () => {
      await operateProcessInstancePage.assertAndEnterModificationMode();
    });

    await test.step('Add new variable "test2"', async () => {
      await operateProcessInstancePage.addNewVariableInModificationMode(
        'newVariables[0]',
        'test2',
        '1',
      );
      await operateProcessInstancePage.assertLastModificationIs(
        /add new variable "test2"/i,
      );
    });

    await test.step('Add new variable "test3"', async () => {
      await operateProcessInstancePage.addNewVariableInModificationMode(
        'newVariables[1]',
        'test3',
        '2',
      );
      await operateProcessInstancePage.assertLastModificationIs(
        /add new variable "test3"/i,
      );
    });

    await test.step('Edit first added variable name to "test2-edited"', async () => {
      await operateProcessInstancePage
        .getNewVariableNameFieldSelector('newVariables[0]')
        .clear();
      await operateProcessInstancePage
        .getNewVariableNameFieldSelector('newVariables[0]')
        .fill('test2-edited');
      await operateProcessInstancePage.pressTab();

      await operateProcessInstancePage.assertLastModificationIs(
        /add new variable "test2-edited"/i,
      );
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

    await test.step('Undo last edit – name reverts to "test2"', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.getNewVariableNameFieldSelector(
          'newVariables[0]',
        ),
      ).toHaveValue('test2');
      await operateProcessInstancePage.assertLastModificationIs(
        /add new variable "test3"/i,
      );
    });

    await test.step('Navigate away, undo removes test3 from other scope', async () => {
      await operateProcessInstancePage.instanceHistory
        .getByText(/never fails/i)
        .click();
      await expect(operateProcessInstancePage.noVariablesText).toBeVisible();

      await operateProcessInstancePage.undoModification();
      await operateProcessInstancePage.assertLastModificationIs(
        /add new variable "test2"/i,
      );

      await operateProcessInstancePage.instanceHistory
        .getByText(/process instance modifications/i)
        .click();

      await expect(
        operateProcessInstancePage.getVariableRow('newVariables[0]'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getVariableRow('newVariables[1]'),
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

    await test.step('Undo again – footer disappears and new variable field removed', async () => {
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.lastModificationFooter,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getVariableRow('newVariables[0]'),
      ).toBeHidden();
    });

    await test.step('Add same variable to two scopes then delete one from summary', async () => {
      await operateProcessInstancePage.addNewVariableInModificationMode(
        'newVariables[0]',
        'test2',
        '1',
      );

      await operateProcessInstancePage.instanceHistory
        .getByText(/never fails/i)
        .click();
      await expect(operateProcessInstancePage.noVariablesText).toBeVisible();

      await operateProcessInstancePage.addNewVariableInModificationMode(
        'newVariables[0]',
        'test2',
        '1',
      );

      await operateProcessInstancePage.clickApplyModificationsButton();

      await expect(
        operateProcessInstancePage.getModificationSummaryItem('test2: 1'),
      ).toHaveCount(2);

      await operateProcessInstancePage
        .getNthDeleteVariableModificationButtonInSummary(1)
        .click();
      await operateProcessInstancePage.cancelModificationSummary();

      await expect(
        operateProcessInstancePage.getVariableRow('newVariables[0]'),
      ).toBeHidden();

      await operateProcessInstancePage.instanceHistory
        .getByText(/process instance modifications/i)
        .click();

      await expect(
        operateProcessInstancePage.getVariableRow('newVariables[0]'),
      ).toBeVisible();
    });

    await test.step('Change added variable value then remove from summary', async () => {
      await operateProcessInstancePage
        .getNewVariableValueFieldSelector('newVariables[0]')
        .fill('21');
      await operateProcessInstancePage.pressTab();

      await operateProcessInstancePage.clickApplyModificationsButton();

      await expect(
        operateProcessInstancePage.getModificationSummaryItem('test2: 21'),
      ).toBeVisible();

      await operateProcessInstancePage.clickDeleteVariableModification();
      await operateProcessInstancePage.cancelModificationSummary();

      await expect(
        operateProcessInstancePage.lastModificationFooter,
      ).toBeHidden();
      await expect(
        operateProcessInstancePage.getVariableRow('newVariables[0]'),
      ).toBeHidden();
    });
  });
});
