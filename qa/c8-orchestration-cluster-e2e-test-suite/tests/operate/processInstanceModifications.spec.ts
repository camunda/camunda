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
import {navigateToApp, hideHelperModals} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {assertJsonEqual} from '../../utils/assertJsonEqual';

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

const neverFailsNode = 'neverFails';
const neverFailsHistoryItem = 'Never fails';
const endElement = 'end';

test.beforeAll(async () => {
  await deploy([
    './resources/withoutIncidentsProcess_v_1.bpmn',
    './resources/EmbeddedSubprocess.bpmn',
  ]);
});

test.describe('Process Instance Modifications - Variables in Modification Mode', () => {
  let instanceKey: string;

  test.beforeEach(
    async ({page, loginPage, operateHomePage, operateProcessInstancePage}) => {
      const instance = await createSingleInstance(
        'Process_EmbeddedSubprocess',
        1,
        {
          meow: 0,
          testVariableNumber: 123,
          testVariableString: 'bar',
        },
      );
      instanceKey = instance.processInstanceKey;
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.operateBanner).toBeVisible();
      await hideHelperModals(page);
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: instanceKey,
      });
      await expect(
        operateProcessInstancePage.getVariableTestId('testVariableString'),
      ).toBeVisible();
    },
  );

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Variables are not editable when entering modification mode', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // when
    await operateProcessInstancePage.enterModificationMode();

    // then - add variable button and edit buttons are hidden
    await expect(operateProcessInstancePage.addVariableButton).toBeHidden();
    await expect(
      operateProcessInstancePage.existingVariableByName('testVariableString')
        .editVariableModal.button,
    ).toBeHidden();

    // also verify no variables are addable when selecting a flow node
    await operateProcessInstanceViewModificationModePage.clickFlowNode(
      activityNode,
    );
    await expect(operateProcessInstancePage.addVariableButton).toBeHidden();
  });

  test('Variables become editable after adding a token', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();

    // when
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });

    // then
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

  test('Editing a variable and undoing restores original value', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });

    // when
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

    // then - undo restores original value
    await operateProcessInstanceViewModificationModePage.undoModification();
    await expect(
      operateProcessInstanceViewModificationModePage.getEditVariableFieldSelector(
        'testVariableNumber',
      ),
    ).toHaveText('123');
  });

  test('Adding a duplicate variable name shows a validation error', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });

    // when - add variable with an existing variable name
    await operateProcessInstanceViewModificationModePage.addNewVariable(
      '0',
      'testVariableNumber',
      '7',
    );

    // then - a duplicate name error is shown
    await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
      0,
      'Name should be unique',
      'name',
    );
    await expect(
      operateProcessInstanceViewModificationModePage.newVariableByIndex(0)
        .nameErrorMessage!,
    ).toBeVisible();

    // and resolving with a unique name clears the error
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

  test('Adding a variable with empty name shows a validation error', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });

    const newNestedParentName = `${activityFirstSubprocess}, this element instance is planned to be added`;
    await operateProcessInstancePage.expandTreeItemInHistory(
      newNestedParentName,
    );
    const addedTokenInHistory = `${activityCollectMoney}, this element instance is planned to be added`;
    await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);
    await expect(
      operateProcessInstanceViewModificationModePage.noVariablesText,
    ).toBeVisible();
    await expect(operateProcessInstancePage.addVariableButton).toBeVisible();

    // when - add variable with no name
    await operateProcessInstanceViewModificationModePage.addNewVariable(
      '0',
      '',
      '"addedValue"',
    );

    // then - an empty name error is shown
    await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
      0,
      'Name has to be filled',
      'name',
    );

    // and providing a valid name clears the error
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
  });

  test('JSON editor updates variable value correctly', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });

    const newNestedParentName = `${activityFirstSubprocess}, this element instance is planned to be added`;
    await operateProcessInstancePage.expandTreeItemInHistory(
      newNestedParentName,
    );
    const addedTokenInHistory = `${activityCollectMoney}, this element instance is planned to be added`;
    await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);

    // add a variable and set its value via JSON editor
    await operateProcessInstanceViewModificationModePage.addNewVariable(
      '0',
      'testLocalVariable',
      '"addedValue"',
    );
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
    await operateProcessInstanceViewModificationModePage
      .newVariableByIndex(1)
      .readModeValue.click();
    await page.keyboard.insertText('meow');
    await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
      1,
      'Value has to be JSON',
      'value',
    );

    // when - fix value via JSON editor modal
    await operateProcessInstanceViewModificationModePage.editNewVariableJSONInModal(
      1,
      JSON.stringify(validJSONValue1),
    );

    // then - variable value is updated
    await waitForAssertion({
      assertion: async () => {
        await assertJsonEqual(
          operateProcessInstanceViewModificationModePage.newVariableByIndex(1)
            .writeModeValue,
          validJSONValue1,
        );
      },
      onFailure: async () => {},
    });
    await expect(
      operateProcessInstanceViewModificationModePage.lastAddedModificationText,
    ).toBeVisible();
    await expect(
      operateProcessInstanceViewModificationModePage.getAddVariableModificationText(
        'testEmptyVariable',
      ),
    ).toBeVisible();

    // also verify JSON editor works for existing variables
    await operateProcessInstancePage.navigateToRootScope();
    await operateProcessInstanceViewModificationModePage.editExistingVariableJSONInModal(
      'testVariableString',
      JSON.stringify(validJSONValue2),
    );
    await expect(
      operateProcessInstanceViewModificationModePage.getEditVariableFieldSelector(
        'testVariableString',
      ),
    ).toContainText(JSON.stringify(validJSONValue2, null, 2));
    await expect(
      operateProcessInstanceViewModificationModePage.lastAddedModificationText,
    ).toBeVisible();
    await expect(
      operateProcessInstanceViewModificationModePage.getEditVariableModificationText(
        'testVariableString',
      ),
    ).toBeVisible();
  });

  test('New variable can be deleted via trash icon before applying', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });
    await operateProcessInstancePage.navigateToRootScope();
    await operateProcessInstanceViewModificationModePage.addNewVariable(
      '0',
      'testVariableToRemove',
      '"to be removed"',
    );

    // when - delete via trash icon
    await operateProcessInstanceViewModificationModePage
      .newVariableByIndex(0)
      .deleteButton.click();

    // then - the variable row is immediately removed from the variables panel
    await expect(
      operateProcessInstancePage.getVariableTestId('newVariables[0]'),
    ).toBeHidden();

    // and the variable does not appear in the review dialog
    await operateProcessInstanceViewModificationModePage.clickReviewModificationsButton();
    await operateProcessInstanceViewModificationModePage.expectVariableNotPresentInDialog(
      'testVariableToRemove',
    );
    await operateProcessInstanceViewModificationModePage.clickCancelButtonDialog();
  });

  test('New variable can be removed from the Apply Modifications dialog', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });
    await operateProcessInstancePage.navigateToRootScope();
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

    // when - delete from the review dialog
    await operateProcessInstanceViewModificationModePage.clickReviewModificationsButton();
    await operateProcessInstanceViewModificationModePage.deleteVariableModificationFromDialog(
      {nameText: 'testVariableToMeow: "meow"'},
    );

    // then - variable is no longer listed in the dialog
    await operateProcessInstanceViewModificationModePage.expectVariableNotPresentInDialog(
      'testVariableToMeow',
    );
    await operateProcessInstanceViewModificationModePage.clickCancelButtonDialog();
  });

  test('Applied modifications persist after reloading', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given - enter modification mode and add a token
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      activityCollectMoney,
    );
    await waitForAssertion({
      assertion: async () => {
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          activityCollectMoney,
          1,
        );
      },
      onFailure: async () => {},
    });

    // add a new variable at process level (intentionally use an existing name first to trigger validation,
    // then correct it — this mirrors the setup from the original test)
    await operateProcessInstanceViewModificationModePage.addNewVariable(
      '0',
      'testVariableNumber',
      '7',
    );
    await operateProcessInstanceViewModificationModePage
      .newVariableByIndex(0)
      .name.fill('testNewMeowVariable');

    // edit existing variable via JSON editor
    await operateProcessInstanceViewModificationModePage.editExistingVariableJSONInModal(
      'testVariableString',
      JSON.stringify(validJSONValue2),
    );

    // navigate to the added token and add element-level variables
    const newNestedParentName = `${activityFirstSubprocess}, this element instance is planned to be added`;
    await operateProcessInstancePage.expandTreeItemInHistory(
      newNestedParentName,
    );
    const addedTokenInHistory = `${activityCollectMoney}, this element instance is planned to be added`;
    await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);
    await operateProcessInstanceViewModificationModePage.addNewVariable(
      '1',
      'testLocalVariable',
      '"addedValue"',
    );
    await operateProcessInstanceViewModificationModePage.addNewVariable(
      '2',
      'testEmptyVariable',
      '',
    );
    await operateProcessInstanceViewModificationModePage.editNewVariableJSONInModal(
      2,
      JSON.stringify(validJSONValue1),
    );

    // when - apply all modifications
    await operateProcessInstanceViewModificationModePage.clickReviewModificationsButton();
    await operateProcessInstanceViewModificationModePage.clickApplyButtonModificationsDialog();

    // then - modification mode is exited
    await expect(
      operateProcessInstanceViewModificationModePage.modificationModeText,
    ).toBeHidden();

    // and element-level variables are persisted
    const parentName = activityFirstSubprocess;
    await operateProcessInstancePage.expandTreeItemInHistory(parentName);
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
    await assertJsonEqual(
      operateProcessInstancePage.existingVariableByName('testEmptyVariable')
        .value,
      validJSONValue1,
    );

    // and process-level variables are persisted
    await operateProcessInstancePage.navigateToRootScope();
    await expect(
      operateProcessInstancePage.existingVariableByName('testNewMeowVariable')
        .name,
    ).toBeVisible();
    expect(
      await operateProcessInstancePage
        .existingVariableByName('testNewMeowVariable')
        .value.innerText(),
    ).toContain('7');
    await assertJsonEqual(
      operateProcessInstancePage.existingVariableByName('testVariableString')
        .value,
      validJSONValue2,
    );
  });
});

test.describe('Process Instance Modifications - Add Variable', () => {
  let instanceKey: string;

  test.beforeEach(
    async ({page, loginPage, operateHomePage, operateProcessInstancePage}) => {
      const instance = await createSingleInstance(
        'withoutIncidentsProcess',
        1,
        {
          test: 123,
          foo: 'bar',
        },
      );
      instanceKey = instance.processInstanceKey;
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.operateBanner).toBeVisible();
      await hideHelperModals(page);
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: instanceKey,
      });
      await expect(
        operateProcessInstancePage.getVariableTestId('foo'),
      ).toBeVisible();
    },
  );

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Should add new variables and undo modifications', async ({
    page,
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await expect(operateProcessInstancePage.modificationModeText).toBeVisible();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      neverFailsNode,
    );
    await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
      neverFailsNode,
      1,
    );

    // when - add two new variables
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

    // edit the first variable name
    await operateProcessInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .clear();
    await operateProcessInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .type('test2-edited');
    await page.keyboard.press('Tab');
    await expect(
      operateProcessInstancePage.getAddVariableModificationText('test2-edited'),
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

    // undo: name should revert to 'test2'
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

    // navigate away and undo again
    const addedTokenInHistory = `${neverFailsHistoryItem}, this element instance is planned to be added`;
    await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);
    await expect(operateProcessInstancePage.noVariablesText).toBeVisible();
    await operateProcessInstancePage.undoModification();
    await expect(
      operateProcessInstancePage.lastAddedModificationText,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.getAddVariableModificationText('test2'),
    ).toBeVisible();

    // return to root scope and verify only the first variable remains
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
    ).toHaveText('1');

    // undo again: variable field is removed
    await operateProcessInstancePage.undoModification();
    await expect(
      operateProcessInstancePage.getVariableTestId('newVariables[0]'),
    ).toBeHidden();
  });

  test('Should add variables to different scopes and remove one from summary', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await expect(operateProcessInstancePage.modificationModeText).toBeVisible();
    await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
      neverFailsNode,
    );
    await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
      neverFailsNode,
      1,
    );

    // when - add same variable name at two different scopes
    await operateProcessInstancePage.addNewVariableModificationMode(
      'newVariables[0]',
      'test3',
      '1',
    );

    const addedTokenInHistory = `${neverFailsHistoryItem}, this element instance is planned to be added`;
    await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);
    await expect(operateProcessInstancePage.noVariablesText).toBeVisible();
    await operateProcessInstancePage.addNewVariableModificationMode(
      'newVariables[0]',
      'test3',
      '1',
    );

    // open summary dialog and verify both variables are listed
    await operateProcessInstancePage.clickReviewModifications();
    await expect(
      operateProcessInstancePage.getDialogVariableModificationSummaryText(
        'test3',
        '1',
      ),
    ).toHaveCount(2);

    // remove one variable from the dialog
    await operateProcessInstanceViewModificationModePage.deleteVariableModificationFromDialog(
      {nameText: 'test3: 1', scopeText: 'Never fails'},
    );
    await operateProcessInstancePage.clickDialogCancel();

    // then - the element-scope variable is removed from the pending list
    await expect(
      operateProcessInstancePage.getVariableTestId('newVariables[0]'),
    ).toBeHidden();

    // and the process-scope variable is still scheduled
    await operateProcessInstancePage.clickReviewModifications();
    const row =
      operateProcessInstanceViewModificationModePage.applyModificationDialogVariableModificationRowByIndex(
        0,
      );
    await expect(row.nameValue).toHaveText('test3: 1');
    await expect(row.scope).toHaveText('Without Incidents Process');
  });
});

test.describe('Process Instance Modifications - Edit Variable', () => {
  let instanceKey: string;

  test.beforeEach(
    async ({page, loginPage, operateHomePage, operateProcessInstancePage}) => {
      const instance = await createSingleInstance(
        'withoutIncidentsProcess',
        1,
        {
          test: 123,
          foo: 'bar',
        },
      );
      instanceKey = instance.processInstanceKey;
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await expect(operateHomePage.operateBanner).toBeVisible();
      await hideHelperModals(page);
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: instanceKey,
      });
      await expect(
        operateProcessInstancePage.getVariableTestId('foo'),
      ).toBeVisible();
    },
  );

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Should edit variables and undo modifications', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await expect(operateProcessInstancePage.modificationModeText).toBeVisible();
    await operateProcessInstanceViewModificationModePage.moveInstanceFromSelectedFlowNodeToTarget(
      neverFailsNode,
      endElement,
    );
    await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
      neverFailsNode,
      -1,
    );
    await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
      endElement,
      1,
    );

    // when - edit foo to 1, test to 2, then foo to 3
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
    ).toHaveText('3');

    // undo: foo should revert to 1
    await operateProcessInstancePage.undoModification();
    await expect(
      operateProcessInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveText('1');
    await expect(
      operateProcessInstancePage.lastAddedModificationText,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.getEditVariableModificationText('test'),
    ).toBeVisible();

    // navigate to a different flow node and undo again
    await operateProcessInstancePage.clickFlowNode(neverFailsHistoryItem);
    await expect(operateProcessInstancePage.noVariablesText).toBeVisible();
    await operateProcessInstancePage.undoModification();
    await expect(
      operateProcessInstancePage.lastAddedModificationText,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.getEditVariableModificationText('foo'),
    ).toBeVisible();

    // return to root scope and verify current modification state
    await operateProcessInstancePage.navigateToRootScope();
    await expect(
      operateProcessInstancePage.getEditVariableFieldSelector('test'),
    ).toHaveText('123');
    await expect(
      operateProcessInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveText('1');

    // undo the last remaining variable edit: only the move modification remains
    await operateProcessInstancePage.undoModification();
    await expect(
      operateProcessInstancePage.lastAddedModificationText,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.getMoveInstanceModificationText(
        neverFailsHistoryItem,
        endElement,
      ),
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.getEditVariableFieldSelector('test'),
    ).toHaveText('123');
    await expect(
      operateProcessInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveText('"bar"');
  });

  test('Should remove a variable edit from the Apply Modifications dialog', async ({
    operateProcessInstancePage,
    operateProcessInstanceViewModificationModePage,
  }) => {
    // given
    await operateProcessInstancePage.enterModificationMode();
    await operateProcessInstanceViewModificationModePage.moveInstanceFromSelectedFlowNodeToTarget(
      neverFailsNode,
      endElement,
    );
    await operateProcessInstancePage.editVariableValueModificationMode(
      'foo',
      '2',
    );

    // when - open review dialog and remove the foo variable edit
    await operateProcessInstancePage.clickReviewModifications();
    await expect(
      operateProcessInstancePage.getVariableModificationSummaryText('foo', '2'),
    ).toBeVisible();
    await operateProcessInstanceViewModificationModePage.deleteVariableModificationFromDialog(
      {nameText: 'foo: 2'},
    );
    await operateProcessInstanceViewModificationModePage.clickCancelButtonDialog();

    // then - foo is back to its original value
    await expect(
      operateProcessInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveText('"bar"');
    // and the move modification is still pending
    await expect(
      operateProcessInstancePage.lastAddedModificationText,
    ).toBeVisible();
    await expect(
      operateProcessInstancePage.getMoveInstanceModificationText(
        neverFailsHistoryItem,
        endElement,
      ),
    ).toBeVisible();
  });
});
