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

type ProcessInstance = {
  processInstanceKey: string;
};

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

test.describe('Process Instance Modifications', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.describe('Edit variable modifications', () => {
    let instance: ProcessInstance;

    test.beforeEach(
      async ({
        page,
        loginPage,
        operateHomePage,
        operateProcessInstancePage,
        operateProcessInstanceViewModificationModePage,
      }) => {
        instance = await createSingleInstance('withoutIncidentsProcess', 1, {
          test: 123,
          foo: 'bar',
        });
        await navigateToApp(page, 'operate');
        await loginPage.login('demo', 'demo');
        await expect(operateHomePage.operateBanner).toBeVisible();
        await hideHelperModals(page);
        await operateProcessInstancePage.gotoProcessInstancePage({
          id: instance.processInstanceKey,
        });
        await expect(
          operateProcessInstancePage.getVariableTestId('foo'),
        ).toBeVisible();
        await operateProcessInstancePage.enterModificationMode();
        await expect(
          operateProcessInstancePage.modificationModeText,
        ).toBeVisible();
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
      },
    );

    test('Editing a variable creates a modification entry', async ({
      operateProcessInstancePage,
    }) => {
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

    test('Editing the same variable twice shows the latest value', async ({
      operateProcessInstancePage,
    }) => {
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '1',
      );
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '3',
      );

      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveText('3');
    });

    test('Undoing a variable edit reverts to the previous value', async ({
      operateProcessInstancePage,
    }) => {
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '1',
      );
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '3',
      );
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveText('1');
    });

    test('A variable edit can be removed from the Apply Modifications dialog', async ({
      operateProcessInstancePage,
    }) => {
      await operateProcessInstancePage.editVariableValueModificationMode(
        'foo',
        '2',
      );
      await operateProcessInstancePage.editVariableValueModificationMode(
        'test',
        '1234',
      );
      await operateProcessInstancePage.clickReviewModifications();
      await expect(
        operateProcessInstancePage.getVariableModificationSummaryText(
          'foo',
          '2',
        ),
      ).toBeVisible();
      await operateProcessInstancePage.clickDeleteVariableModification();
      await operateProcessInstancePage.clickCancel();

      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('foo'),
      ).toHaveText('"bar"');
      await expect(
        operateProcessInstancePage.getEditVariableFieldSelector('test'),
      ).toHaveText('123');
    });
  });

  test.describe('Add variable modifications', () => {
    let instance: ProcessInstance;

    test.beforeEach(
      async ({
        page,
        loginPage,
        operateHomePage,
        operateProcessInstancePage,
        operateProcessInstanceViewModificationModePage,
      }) => {
        instance = await createSingleInstance('withoutIncidentsProcess', 1, {
          test: 123,
          foo: 'bar',
        });
        await navigateToApp(page, 'operate');
        await loginPage.login('demo', 'demo');
        await expect(operateHomePage.operateBanner).toBeVisible();
        await hideHelperModals(page);
        await operateProcessInstancePage.gotoProcessInstancePage({
          id: instance.processInstanceKey,
        });
        await expect(
          operateProcessInstancePage.getVariableTestId('foo'),
        ).toBeVisible();
        await operateProcessInstancePage.enterModificationMode();
        await expect(
          operateProcessInstancePage.modificationModeText,
        ).toBeVisible();
        await operateProcessInstanceViewModificationModePage.addTokenToFlowNode(
          neverFailsNode,
        );
        await operateProcessInstanceViewModificationModePage.verifyModificationOverlay(
          neverFailsNode,
          1,
        );
      },
    );

    test('Adding a new variable creates a modification entry', async ({
      operateProcessInstancePage,
    }) => {
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

    test('Editing an added variable name updates the modification entry', async ({
      page,
      operateProcessInstancePage,
    }) => {
      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test2',
        '1',
      );
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
    });

    test('Undoing a variable name edit reverts to the original name', async ({
      page,
      operateProcessInstancePage,
    }) => {
      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test2',
        '1',
      );
      await operateProcessInstancePage
        .getNewVariableNameFieldSelector('newVariables[0]')
        .clear();
      await operateProcessInstancePage
        .getNewVariableNameFieldSelector('newVariables[0]')
        .type('test2-edited');
      await page.keyboard.press('Tab');
      await operateProcessInstancePage.undoModification();

      await expect(
        operateProcessInstancePage.getNewVariableNameFieldSelector(
          'newVariables[0]',
        ),
      ).toHaveValue('test2');
    });

    test('Variables with the same name in different scopes are tracked as separate modifications', async ({
      page,
      operateProcessInstancePage,
    }) => {
      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test3',
        '1',
      );
      const addedTokenInHistory = `${neverFailsHistoryItem}, this element instance is planned to be added`;
      await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);
      await expect(
        page.getByText(/The element has no Variables/i),
      ).toBeVisible();

      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test3',
        '1',
      );

      await operateProcessInstancePage.clickReviewModifications();
      await expect(
        operateProcessInstancePage.getDialogVariableModificationSummaryText(
          'test3',
          '1',
        ),
      ).toHaveCount(2);
      await operateProcessInstancePage.clickDialogCancel();
    });

    test('A variable modification from a specific scope can be removed from the Apply Modifications dialog', async ({
      page,
      operateProcessInstancePage,
      operateProcessInstanceViewModificationModePage,
    }) => {
      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test3',
        '1',
      );
      const addedTokenInHistory = `${neverFailsHistoryItem}, this element instance is planned to be added`;
      await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);
      await expect(
        page.getByText(/The element has no Variables/i),
      ).toBeVisible();

      await operateProcessInstancePage.addNewVariableModificationMode(
        'newVariables[0]',
        'test3',
        '1',
      );

      await operateProcessInstancePage.clickReviewModifications();
      await operateProcessInstanceViewModificationModePage.deleteVariableModificationFromDialog(
        {nameText: 'test3: 1', scopeText: 'Never fails'},
      );
      await operateProcessInstancePage.clickDialogCancel();

      await operateProcessInstancePage.navigateToRootScope();
      await expect(
        operateProcessInstancePage.getVariableTestId('newVariables[0]'),
      ).toBeHidden();

      await operateProcessInstancePage.clickReviewModifications();
      const row =
        operateProcessInstanceViewModificationModePage.applyModificationDialogVariableModificationRowByIndex(
          0,
        );
      await expect(row.nameValue).toHaveText('test3: 1');
      await expect(row.scope).toHaveText('Without Incidents Process');
    });
  });

  test.describe('Variables functionality in modification mode', () => {
    let instance: ProcessInstance;

    test.beforeEach(
      async ({
        page,
        loginPage,
        operateHomePage,
        operateProcessInstancePage,
      }) => {
        instance = await createSingleInstance('Process_EmbeddedSubprocess', 1, {
          meow: 0,
          testVariableNumber: 123,
          testVariableString: 'bar',
        });
        await navigateToApp(page, 'operate');
        await loginPage.login('demo', 'demo');
        await expect(operateHomePage.operateBanner).toBeVisible();
        await hideHelperModals(page);
        await operateProcessInstancePage.gotoProcessInstancePage({
          id: instance.processInstanceKey,
        });
        await expect(
          operateProcessInstancePage.getVariableTestId('testVariableString'),
        ).toBeVisible();
      },
    );

    test('Variables are not editable when entering modification mode', async ({
      operateProcessInstancePage,
    }) => {
      await operateProcessInstancePage.enterModificationMode();

      await expect(operateProcessInstancePage.addVariableButton).toBeHidden();
      await expect(
        operateProcessInstancePage.existingVariableByName('testVariableString')
          .editVariableModal.button,
      ).toBeHidden();
    });

    test('Unselected flow node has no add variable button in modification mode', async ({
      operateProcessInstancePage,
      operateProcessInstanceViewModificationModePage,
    }) => {
      await operateProcessInstancePage.enterModificationMode();
      await operateProcessInstanceViewModificationModePage.clickFlowNode(
        activityNode,
      );

      await expect(operateProcessInstancePage.addVariableButton).toBeHidden();
    });

    test.describe('after adding a token to CollectMoney', () => {
      test.beforeEach(
        async ({
          operateProcessInstancePage,
          operateProcessInstanceViewModificationModePage,
        }) => {
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
        },
      );

      test('Variables become editable after adding a token', async ({
        operateProcessInstancePage,
        operateProcessInstanceViewModificationModePage,
      }) => {
        await expect(
          operateProcessInstancePage.addVariableButton,
        ).toBeVisible();
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
        operateProcessInstanceViewModificationModePage,
      }) => {
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
        ).toHaveText('123');
      });

      test('Adding a duplicate variable name shows a validation error', async ({
        operateProcessInstanceViewModificationModePage,
      }) => {
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
      });

      test('Adding a variable with an empty name shows a validation error', async ({
        operateProcessInstancePage,
        operateProcessInstanceViewModificationModePage,
      }) => {
        const newNestedParentName = `${activityFirstSubprocess}, this element instance is planned to be added`;
        await operateProcessInstancePage.expandTreeItemInHistory(
          newNestedParentName,
        );
        const addedTokenInHistory = `${activityCollectMoney}, this element instance is planned to be added`;
        await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);

        await expect(
          operateProcessInstanceViewModificationModePage.noVariablesText,
        ).toBeVisible();
        await expect(
          operateProcessInstancePage.addVariableButton,
        ).toBeVisible();

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
      });

      test('JSON editor updates variable value correctly', async ({
        page,
        operateProcessInstancePage,
        operateProcessInstanceViewModificationModePage,
      }) => {
        const newNestedParentName = `${activityFirstSubprocess}, this element instance is planned to be added`;
        await operateProcessInstancePage.expandTreeItemInHistory(
          newNestedParentName,
        );
        const addedTokenInHistory = `${activityCollectMoney}, this element instance is planned to be added`;
        await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);

        await operateProcessInstanceViewModificationModePage.addNewVariable(
          '0',
          'testEmptyVariable',
          '',
        );
        await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
          0,
          'Value has to be filled',
          'value',
        );

        await operateProcessInstanceViewModificationModePage
          .newVariableByIndex(0)
          .readModeValue.click();
        await page.keyboard.insertText('meow');
        await operateProcessInstanceViewModificationModePage.checkNewVariableErrorMessageText(
          0,
          'Value has to be JSON',
          'value',
        );

        await operateProcessInstanceViewModificationModePage.editNewVariableJSONInModal(
          0,
          JSON.stringify(validJSONValue1),
        );

        await waitForAssertion({
          assertion: async () => {
            await assertJsonEqual(
              operateProcessInstanceViewModificationModePage.newVariableByIndex(
                0,
              ).writeModeValue,
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
      });

      test('New variable can be deleted via trash icon before applying', async ({
        page,
        operateProcessInstancePage,
        operateProcessInstanceViewModificationModePage,
      }) => {
        await operateProcessInstancePage.navigateToRootScope();
        await operateProcessInstanceViewModificationModePage.addNewVariable(
          '0',
          'testVariableToRemove',
          '"to be removed"',
        );
        await operateProcessInstanceViewModificationModePage
          .newVariableByIndex(0)
          .deleteButton.click();

        await expect(page.getByTestId('variable-newVariables[0]')).toBeHidden();

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

        await operateProcessInstanceViewModificationModePage.clickReviewModificationsButton();
        await operateProcessInstanceViewModificationModePage.deleteVariableModificationFromDialog(
          {nameText: 'testVariableToMeow: "meow"'},
        );
        await operateProcessInstanceViewModificationModePage.expectVariableNotPresentInDialog(
          'testVariableToMeow',
        );
        await operateProcessInstanceViewModificationModePage.clickCancelButtonDialog();
      });

      test('Applied modifications persist after reloading', async ({
        operateProcessInstancePage,
        operateProcessInstanceViewModificationModePage,
      }) => {
        const newNestedParentName = `${activityFirstSubprocess}, this element instance is planned to be added`;
        await operateProcessInstancePage.expandTreeItemInHistory(
          newNestedParentName,
        );
        const addedTokenInHistory = `${activityCollectMoney}, this element instance is planned to be added`;
        await operateProcessInstancePage.clickTreeItem(addedTokenInHistory);

        await operateProcessInstanceViewModificationModePage.addNewVariable(
          '0',
          'testLocalVariable',
          '"addedValue"',
        );

        await operateProcessInstancePage.navigateToRootScope();
        await operateProcessInstanceViewModificationModePage.addNewVariable(
          '0',
          'testNewMeowVariable',
          '7',
        );
        await operateProcessInstanceViewModificationModePage.editExistingVariableJSONInModal(
          'testVariableString',
          JSON.stringify(validJSONValue2),
        );

        await expect(
          operateProcessInstanceViewModificationModePage.lastAddedModificationText,
        ).toBeVisible();

        await operateProcessInstanceViewModificationModePage.clickReviewModificationsButton();
        await operateProcessInstanceViewModificationModePage.clickApplyButtonModificationsDialog();

        await expect(
          operateProcessInstanceViewModificationModePage.modificationModeText,
        ).toBeHidden();

        await operateProcessInstancePage.expandTreeItemInHistory(
          activityFirstSubprocess,
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

        await operateProcessInstancePage.navigateToRootScope();
        await expect(
          operateProcessInstancePage.existingVariableByName(
            'testNewMeowVariable',
          ).name,
        ).toBeVisible();
        expect(
          await operateProcessInstancePage
            .existingVariableByName('testNewMeowVariable')
            .value.innerText(),
        ).toContain('7');
        await assertJsonEqual(
          operateProcessInstancePage.existingVariableByName(
            'testVariableString',
          ).value,
          validJSONValue2,
        );
      });
    });
  });
});
