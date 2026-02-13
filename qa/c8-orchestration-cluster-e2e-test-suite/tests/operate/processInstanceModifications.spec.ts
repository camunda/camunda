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
import {waitForAssertion} from 'utils/waitForAssertion';

type ProcessInstance = {
  processInstanceKey: string;
};

let instanceWithoutAnIncident: ProcessInstance;
let embeddedSubprocessInstance: ProcessInstance;

const activityNode = 'Node';
const activityFirstSubprocess = 'FirstSubprocess';
const activityCollectMoney = 'CollectMoney';
const validJSONValue1 = {
  employeeId: {
    value: 'E123',
    type: 'String',
  },
  employeeName: {
    value: 'Alice Smith',
    type: 'String',
  },
  employeeEmail: {
    value: 'alice@example.com',
    type: 'String',
  },
  employeeRole: {
    value: 'Developer',
    type: 'String',
  },
  documentsVerified: {
    value: true,
    type: 'Boolean',
  },
  startDate: {
    value: '2025-08-01',
    type: 'String',
  },
};
const validJSONValue2 = {
  employeeId: {
    value: 'E123',
    type: 'String',
  },
};

test.beforeAll(async () => {
  await deploy([
    './resources/withoutIncidentsProcess_v_1.bpmn',
    './resources/EmbeddedSubprocess.bpmn',
  ]);

  instanceWithoutAnIncident = await createSingleInstance(
    'withoutIncidentsProcess',
    1,
    {
      test: 123,
      foo: 'bar',
    },
  );

  embeddedSubprocessInstance = await createSingleInstance(
    'Process_EmbeddedSubprocess',
    1,
    {
      meow: 0,
      testVariableNumber: 123,
      testVariableString: 'bar',
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

  // skipped due to bug 41143: https://github.com/camunda/camunda/issues/41143
  test.skip('Should apply/remove edit variable modifications', async ({
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

  test('Verify Variables functionality in Modification mode', async ({
    operateProcessInstancePage,
    page,
    operateProcessInstanceViewModificationModePage,
  }) => {
    await test.step('Navigate to embedded subprocess instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: embeddedSubprocessInstance.processInstanceKey,
      });
    });

    await test.step('Verify initial variables are visible', async () => {
      await expect(
        operateProcessInstancePage.getVariableTestId('testVariableString'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getVariableTestId('testVariableNumber'),
      ).toBeVisible();
    });

    await test.step('Enter modification mode and verify variables are not editable', async () => {
      await operateProcessInstancePage.enterModificationMode();
      await expect(operateProcessInstancePage.addVariableButton).toBeHidden();
      await expect(operateProcessInstancePage.existingVariableByName('testVariableString').editVariableModal.button).toBeHidden();
    });

    await test.step('Select an element from the process diagram and verify variables are not addable', async () => {
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        activityNode,
      );
      await expect(operateProcessInstancePage.addVariableButton).toBeHidden();
    });

    await test.step('Add a token to the element and verify variables are changeable', async () => {
      await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
        activityCollectMoney,
      );
      await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
        activityCollectMoney,
        1,
      );

      await expect(operateProcessInstancePage.addVariableButton).toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.getEditVariableFieldSelector(
          'testVariableString',
        ),
      ).toBeEnabled();
      await expect(
        operateProcessInstanceViewModificationModePage.getEditVariableFieldSelector(
          'testVariableNumber',
        ),
      ).toBeEnabled();
    });

    await test.step('Change process-level variable and undo changes and verify results', async () => {
      await operateProcessInstanceViewModificationModePage.editVariableValue(
        'testVariableNumber',
        '1',
      );
      await expect(
        operateProcessInstanceViewModificationModePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.getEditVariableModificationText(
          'testVariableNumber',
        ),
      ).toBeVisible();

      await operateProcessInstanceViewModificationModePage.undoModification();
      await expect(
        operateProcessInstanceViewModificationModePage.getEditVariableFieldSelector(
          'testVariableNumber',
        ),
      ).toHaveValue('123');
    });

    await test.step('Add variable on process-level and verify warning appeared', async () => {
      await operateProcessInstanceViewModificationModePage.addNewVariable(
        '0',
        'testVariableNumber',
        '7',
      );
      await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
        0,
        'Name should be unique',
        'name',
      );
      await expect(
        operateProcessInstanceViewModificationModePage.newVariableByIndex(0)
          .nameErrorMessage!,
      ).toBeVisible();
      await operateProcessInstanceViewModificationModePage
        .newVariableByIndex(0)
        .name.fill('testNewMeowVariable');
      await page.keyboard.press('Tab');
      await expect(
        operateProcessInstanceViewModificationModePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.getAddVariableModificationText(
          'testNewMeowVariable',
        ),
      ).toBeVisible();
    });

    await test.step('Select the newly added token from the Instance History and verify the Add Variable button is visible in the Variables panel for the selected token (element level).', async () => {
      const newNestedParentName = `${activityFirstSubprocess}, this flow node instance is planned to be added`;
      await operateProcessInstancePage.expandTreeItemInHistory(
        newNestedParentName,
      );
      const addedTokenInHistory = `${activityCollectMoney}, this flow node instance is planned to be added`;
      await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);

      await expect(
        operateProcessInstanceViewModificationModePage.noVariablesText,
      ).toBeVisible();
      await expect(operateProcessInstancePage.addVariableButton).toBeVisible();

      await operateProcessInstanceViewModificationModePage.addNewVariable(
        '0',
        '',
        '"addedValue"',
      );
      await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
        0,
        'Name has to be filled',
        'name',
      );
      await operateProcessInstanceViewModificationModePage
        .newVariableByIndex(0)
        .name.fill('testLocalVariable');
      await page.keyboard.press('Tab');
      await expect(
        operateProcessInstanceViewModificationModePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.getAddVariableModificationText(
          'testLocalVariable',
        ),
      ).toBeVisible();

      await operateProcessInstanceViewModificationModePage.addNewVariable(
        '1',
        'testEmptyVariable',
        '',
      );
      await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
        1,
        'Value has to be filled',
        'value',
      );
    });

    await test.step('Update variable value in JSON editor modal window and verify results', async () => {
      await operateProcessInstanceViewModificationModePage
        .newVariableByIndex(1)
        .value.fill('meow');
      await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
        1,
        'Value has to be JSON',
        'value',
      );
      await operateProcessInstanceViewModificationModePage.editNewVariableJSONInModal(
        1,
        JSON.stringify(validJSONValue1),
      );
      
      await expect(
        operateProcessInstanceViewModificationModePage.newVariableByIndex(1)
          .value,
      ).toHaveValue(JSON.stringify(validJSONValue1));

      await expect(
        operateProcessInstanceViewModificationModePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.getAddVariableModificationText(
          'testEmptyVariable',
        ),
      ).toBeVisible();
    });

    await test.step('Update variable value for existing variable in JSON editor modal window and verify results', async () => {
      await operateProcessInstancePage.navigateToRootScope();

      await operateProcessInstanceViewModificationModePage.editExistingVariableJSONInModal(
        'testVariableString',
        JSON.stringify(validJSONValue2),
      );
      await expect(
        operateProcessInstanceViewModificationModePage.getEditVariableFieldSelector(
          'testVariableString',
        ),
      ).toHaveValue(JSON.stringify(validJSONValue2));

      await expect(
        operateProcessInstanceViewModificationModePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.getEditVariableModificationText(
          'testVariableString',
        ),
      ).toBeVisible();
    });

    await test.step('Check that a variable that hasn\'t been added yet can be removed with "trash bin" icon', async () => {
      await operateProcessInstanceViewModificationModePage.addNewVariable(
        '0',
        'testVariableToRemove',
        '"to be removed"',
      );
      await operateProcessInstanceViewModificationModePage
        .newVariableByIndex(0)
        .deleteButton.click();

      await operateProcessInstanceViewModificationModePage.clickApplyModificationsButton();
      for (let i = 0; ; i++) {
        const row =
          operateProcessInstanceViewModificationModePage.applyModificationDialogVariableModificationRowByIndex(
            i,
          );
        if (!(await row.nameValue.isVisible())) {
          break;
        }
        const variableName = await row.nameValue.innerText();
        expect(variableName).not.toContain('testVariableToRemove');
      }
      await operateProcessInstanceViewModificationModePage.clickCancelButtonDialog();
    });

    await test.step('Check that a variable that hasn\'t been added yet can be removed from "Apply Modifications" window', async () => {
      await operateProcessInstanceViewModificationModePage.addNewVariable(
        '0',
        'testVariableToMeow',
        '"meow"',
      );

      await expect(
        operateProcessInstanceViewModificationModePage.lastAddedModificationText,
      ).toBeVisible();
      await expect(
        operateProcessInstanceViewModificationModePage.getAddVariableModificationText(
          'testVariableToMeow',
        ),
      ).toBeVisible();

      await operateProcessInstanceViewModificationModePage.clickApplyModificationsButton();

      let variableModificationFound = false;

      for (let i = 0; ; i++) {
        const row =
          operateProcessInstanceViewModificationModePage.applyModificationDialogVariableModificationRowByIndex(
            i,
          );
        if (!(await row.nameValue.isVisible())) {
          break;
        }
        const variableNameValue = await row.nameValue.innerText();
        if (variableNameValue === 'testVariableToMeow: "meow"') {
          await expect(row.nameValue).toHaveText('testVariableToMeow: "meow"');
          await row.deleteVariableModificationButton.click();
          variableModificationFound = true;
          break;
        }
      }
      expect(variableModificationFound).toBeTruthy();

      for (let i = 0; ; i++) {
        const row =
          operateProcessInstanceViewModificationModePage.applyModificationDialogVariableModificationRowByIndex(
            i,
        );
        if (!(await row.nameValue.isVisible())) {
          break;
        }
        const variableName = await row.nameValue.innerText();
    
        expect(variableName).not.toContain('testVariableToMeow');
      }

      await operateProcessInstanceViewModificationModePage.clickCancelButtonDialog();
    });

    await test.step('Apply modifications and verify variable values in the instance', async () => {
      await operateProcessInstanceViewModificationModePage.clickApplyModificationsButton();
      await operateProcessInstanceViewModificationModePage.clickApplyButtonModificationsDialog();

      await expect(
        operateProcessInstanceViewModificationModePage.modificationModeText,
      ).toBeHidden();
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.existingVariableByName(
              'testNewMeowVariable',
            ).name,
          ).toBeVisible({timeout: 5000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      expect(
        await operateProcessInstancePage
          .existingVariableByName('testNewMeowVariable')
          .value.innerText(),
      ).toContain('7');
      expect(
        await operateProcessInstancePage
          .existingVariableByName('testVariableString')
          .value.innerText(),
      ).toContain(JSON.stringify(validJSONValue2));

      const newNestedParentName = activityFirstSubprocess;
      await operateProcessInstancePage.expandTreeItemInHistory(
        newNestedParentName,
      );
      await operateProcessInstancePage.clickInstanceHistoryElement(
        activityCollectMoney,
      );
      await expect(
        operateProcessInstancePage.existingVariableByName('testLocalVariable')
          .name,
      ).toBeVisible();
      expect(
        await operateProcessInstancePage
          .existingVariableByName('testLocalVariable')
          .value.innerText(),
      ).toContain('"addedValue"');
      await expect(
        operateProcessInstancePage.existingVariableByName('testLocalVariable')
          .name,
      ).toBeVisible();
      expect(
        await operateProcessInstancePage
          .existingVariableByName('testEmptyVariable')
          .value.innerText(),
      ).toContain(JSON.stringify(validJSONValue1));
    });

    await test.step("Check that an existing variable can't be deleted:", async () => {
      await operateProcessInstancePage.navigateToRootScope();
      await operateProcessInstancePage.clickEditVariableButton(
        'testVariableNumber',
      );
      await operateProcessInstancePage.clearVariableValueInput();
      await page.keyboard.press('Tab');
      await operateProcessInstancePage.checkExistingVariableErrorMessageText(
        'testVariableNumber',
        'Value has to be JSON',
      );
      await expect(
        operateProcessInstancePage.saveVariableButton,
      ).toBeDisabled();
      await operateProcessInstancePage.fillVariableValueInput('1');
      await expect(operateProcessInstancePage.saveVariableButton).toBeEnabled();
      await operateProcessInstancePage.saveVariableButton.click();

      await page.reload();
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.existingVariableByName(
              'testVariableNumber',
            ).value,
          ).toHaveText('1', {timeout: 5000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });
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
