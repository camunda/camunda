/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

export class OperateProcessInstanceViewModificationModePage {
  private page: Page;
  readonly modifyModeHeader: Locator;
  readonly flowNodeModificationsPopup: Locator;
  readonly addModificationButtononPopup: Locator;
  readonly moveAllButtononPopup: Locator;
  readonly cancelButtonPopup: Locator;
  readonly cancelAllButtonPopup: Locator;
  readonly applyModificationsButton: Locator;
  readonly discardAllModificationsButton: Locator;
  readonly cancelButtonModificationDialog: Locator;
  readonly applyButtonModificationsDialog: Locator;
  readonly moveTokensMessage: Locator;
  readonly diagram: Locator;
  readonly multipleInstancesAlert: Locator;
  readonly history: Locator;
  readonly continueButton: Locator;
  readonly addSingleFlowNodeInstanceButton: Locator;
  readonly moveSelectedInstanceButton: Locator;
  readonly modificationModeText: Locator;
  readonly lastAddedModificationText: Locator;
  readonly undoModificationButton: Locator;
  readonly deleteVariableModificationButton: Locator;
  readonly cancelButton: Locator;
  readonly addVariableModificationButton: Locator;
  readonly modalDialog: Locator;
  readonly noVariablesText: Locator;
  readonly newVariableByIndex: (index: number) => {
    name: Locator;
    value: Locator;
    jsonEditorButton: Locator;
    deleteButton: Locator;
    jsonEditorModal: {
      header: Locator;
      cancelButton: Locator;
      applyButton: Locator;
      inputField: Locator;
    };
    valueErrorMessage?: Locator;
    nameErrorMessage?: Locator;
  };
  readonly editableExistingVariableByName: (name: string) => {
    name: Locator;
    value: Locator;
    jsonEditorButton: Locator;
    jsonEditorModal: {
      header: Locator;
      cancelButton: Locator;
      applyButton: Locator;
      inputField: Locator;
    };
    valueErrorMessage?: Locator;
  };
  readonly applyModificationDialog: Locator;
  readonly applyModificationDialogFlowNodeModificationRowByIndex: (
    index: number,
  ) => {
    operation: Locator;
    flowNode: Locator;
    instanceKey: Locator;
    affectedTokens: Locator;
    deleteFlowNodeModificationButton: Locator;
  };
  readonly applyModificationDialogVariableModificationRowByIndex: (
    index: number,
  ) => {
    expandChangesButton: Locator;
    operation: Locator;
    scope: Locator;
    nameValue: Locator;
    childRow: Locator;
    deleteVariableModificationButton: Locator;
  };

  constructor(page: Page) {
    this.page = page;
    this.modifyModeHeader = page
      .getByText('Process Instance Modification Mode')
      .first();
    this.flowNodeModificationsPopup = page.getByText('Flow Node Modifications');
    this.addModificationButtononPopup = page.getByTitle(
      'Add single flow node instance',
    );
    this.moveAllButtononPopup = page.getByTitle(
      'Move all running instances in this flow node to another target',
    );
    this.cancelButtonPopup = page.getByTitle(
      'Cancel selected instance in this flow node',
    );
    this.cancelAllButtonPopup = page.getByTitle(
      'Cancel all running flow node instances in this flow node',
    );
    this.applyModificationsButton = page.getByTestId(
      'apply-modifications-button',
    );
    this.discardAllModificationsButton = page.getByTestId('discard-all-button');
    this.cancelButtonModificationDialog = page
      .getByRole('dialog')
      .getByRole('button', {name: 'Cancel'});
    this.applyButtonModificationsDialog = page
      .getByRole('dialog')
      .getByRole('button', {name: 'Apply'});
    this.moveTokensMessage = page.getByText(
      'Select the target flow node in the diagram',
    );
    this.diagram = this.page.getByTestId('diagram');
    this.multipleInstancesAlert = page.getByText(
      'Flow node has multiple instances. To select one, use the instance history tree below.',
    );
    this.history = page.getByTestId('instance-history');

    this.continueButton = page.getByRole('button', {name: 'Continue'});
    this.addSingleFlowNodeInstanceButton = page.getByRole('button', {
      name: 'Add single flow node instance',
    });
    this.moveSelectedInstanceButton = page.getByRole('button', {
      name: 'Move selected instance in this flow node to another target',
    });
    this.modificationModeText = page.getByText(
      'Process Instance Modification Mode',
    );
    this.lastAddedModificationText = page.getByText('Last added modification:');
    this.undoModificationButton = page.getByRole('button', {name: 'undo'});
    this.deleteVariableModificationButton = page.getByRole('button', {
      name: /delete variable modification/i,
    });
    this.cancelButton = page.getByRole('button', {name: 'Cancel'});
    this.addVariableModificationButton = page.getByRole('button', {
      name: /add variable/i,
    });
    this.modalDialog = page.getByRole('dialog');
    this.noVariablesText = page.getByText(/The Flow Node has no Variables/i);
    this.newVariableByIndex = (index: number) => ({
      name: this.page
        .getByTestId(`variable-newVariables[${index}]`)
        .locator(`[id="newVariables[${index}].name"]`),
      value: this.page
        .getByTestId(`variable-newVariables[${index}]`)
        .locator(`[id="newVariables[${index}].value"]`),
      jsonEditorButton: this.page
        .getByTestId(`variable-newVariables[${index}]`)
        .getByRole('cell')
        .nth(1)
        .getByRole('button'),
      deleteButton: this.page
        .getByTestId(`variable-newVariables[${index}]`)
        .getByRole('cell')
        .nth(2)
        .getByRole('button'),
      jsonEditorModal: {
        header: this.page
          .getByTestId(`variable-newVariables[${index}]`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByText('Edit a new Variable'),
        cancelButton: this.page
          .getByTestId(`variable-newVariables[${index}]`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByRole('button', {name: 'Cancel'}),
        applyButton: this.page
          .getByTestId(`variable-newVariables[${index}]`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByRole('button', {name: 'Apply'}),
        inputField: this.page
          .getByTestId(`variable-newVariables[${index}]`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByRole('code')
          .getByRole('textbox', { name: 'Editor content' }),
      },
      valueErrorMessage: this.page
        .getByTestId(`variable-newVariables[${index}]`)
        .getByRole('cell')
        .nth(1)
        .locator(`[id="newVariables[${index}].value-error-msg"]`),
      nameErrorMessage: this.page
        .getByTestId(`variable-newVariables[${index}]`)
        .getByRole('cell')
        .nth(0)
        .locator(`[id="newVariables[${index}].name-error-msg"]`),
    });

    this.editableExistingVariableByName = (name: string) => ({
      name: this.page.getByTestId(`variable-${name}`).getByTitle(name),
      value: this.page
        .getByTestId(`variable-${name}`)
        .getByTestId('edit-variable-value'),
      jsonEditorButton: this.page
        .getByTestId(`variable-${name}`)
        .getByRole('cell')
        .nth(1)
        .getByRole('button'),
      jsonEditorModal: {
        header: this.page
          .getByTestId(`variable-${name}`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByText(`Edit Variable "${name}"`),
        cancelButton: this.page
          .getByTestId(`variable-${name}`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByRole('button', {name: 'Cancel'}),
        applyButton: this.page
          .getByTestId(`variable-${name}`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByRole('button', {name: 'Apply'}),
        inputField: this.page
          .getByTestId(`variable-${name}`)
          .getByRole('cell')
          .nth(1)
          .getByRole('presentation')
          .getByRole('dialog')
          .getByRole('code')
          .getByRole('textbox', { name: 'Editor content' }),
      },
      valueErrorMessage: this.page
        .getByTestId(`variable-${name}`)
        .locator(`[id="${name}-error-msg"]`),
    });
    this.applyModificationDialog = this.page.getByRole('dialog', {
      name: 'Apply Modifications',
    });
    this.applyModificationDialogFlowNodeModificationRowByIndex = (
      index: number,
    ) => ({
      operation: this.applyModificationDialog
        .getByRole('table')
        .nth(0)
        .locator('tbody')
        .getByRole('row')
        .nth(index)
        .getByRole('cell')
        .nth(1),
      flowNode: this.applyModificationDialog
        .getByRole('table')
        .nth(0)
        .locator('tbody')
        .getByRole('row')
        .nth(index)
        .getByRole('cell')
        .nth(2),
      instanceKey: this.applyModificationDialog
        .getByRole('table')
        .nth(0)
        .locator('tbody')
        .getByRole('row')
        .nth(index)
        .getByRole('cell')
        .nth(3),
      affectedTokens: this.applyModificationDialog
        .getByRole('table')
        .nth(0)
        .locator('tbody')
        .getByRole('row')
        .nth(index)
        .getByTestId('affected-token-count'),
      deleteFlowNodeModificationButton: this.applyModificationDialog
        .getByRole('table')
        .nth(0)
        .locator('tbody')
        .getByRole('row')
        .nth(index)
        .getByRole('cell')
        .nth(5)
        .getByRole('button', {name: 'Delete flow node modification'}),
    });
    this.applyModificationDialogVariableModificationRowByIndex = (
      index: number,
    ) => ({
      expandChangesButton: this.applyModificationDialog
        .getByRole('table')
        .nth(1)
        .locator('tr[data-parent-row="true"]')
        .nth(index)
        .getByRole('cell')
        .nth(0)
        .getByRole('button'),
      operation: this.applyModificationDialog
        .getByRole('table')
        .nth(1)
        .locator('tr[data-parent-row="true"]')
        .nth(index)
        .getByRole('cell')
        .nth(1),
      scope: this.applyModificationDialog
        .getByRole('table')
        .nth(1)
        .locator('tr[data-parent-row="true"]')
        .nth(index)
        .getByRole('cell')
        .nth(2),
      nameValue: this.applyModificationDialog
        .getByRole('table')
        .nth(1)
        .locator('tr[data-parent-row="true"]')
        .nth(index)
        .getByRole('cell')
        .nth(3),
      childRow: this.applyModificationDialog
        .getByRole('table')
        .nth(1)
        .locator('tr[data-child-row="true"]')
        .nth(index),
      deleteVariableModificationButton: this.applyModificationDialog
        .getByRole('table')
        .nth(1)
        .locator('tr[data-parent-row="true"]')
        .nth(index)
        .getByRole('cell')
        .nth(5)
        .getByRole('button', {name: 'Delete variable modification'}),
    });
  }

  async clickAddModificationButtononPopup(): Promise<void> {
    await this.addModificationButtononPopup.click();
  }

  async clickMoveAllButtononPopup(): Promise<void> {
    await this.moveAllButtononPopup.click();
  }

  async clickCancelButtononPopup(): Promise<void> {
    await this.cancelButtonPopup.click();
  }

  async clickCancelAllButtononPopup(): Promise<void> {
    await this.cancelAllButtonPopup.click();
  }

  async clickApplyModificationsButton(): Promise<void> {
    await this.applyModificationsButton.click();
  }

  async clickDiscardAllModificationsButton(): Promise<void> {
    await this.discardAllModificationsButton.click();
  }

  async clickCancelButtonDialog(): Promise<void> {
    await this.cancelButtonModificationDialog.click();
  }

  async clickApplyButtonModificationsDialog(): Promise<void> {
    await this.applyButtonModificationsDialog.click();
  }

  clickFlowNode(flowNodeName: string) {
    return this.getFlowNode(flowNodeName).first().click({timeout: 20000});
  }

  clickSubProcess(subProcessName: string) {
    return this.getFlowNode(subProcessName).click({
      position: {x: 5, y: 5},
      force: true,
    });
  }

  getFlowNode(flowNodeName: string) {
    return this.diagram
      .locator('.djs-group')
      .locator(`[data-element-id="${flowNodeName}"]`);
  }

  async addTokenToFlowNodeAndApplyChanges(flowNodeName: string): Promise<void> {
    await this.clickFlowNode(flowNodeName);
    await this.clickAddModificationButtononPopup();
    await this.applyChanges();
  }

  async addTokenToSubprocessAndApplyChanges(
    flowNodeName: string,
  ): Promise<void> {
    await this.clickSubProcess(flowNodeName);
    await this.clickAddModificationButtononPopup();
    await this.applyChanges();
  }

  async moveAllTokensFromSelectedFlowNodeToTarget(
    sourceFlowNodeName: string,
    targetFlowNodeName: string,
  ): Promise<void> {
    await this.clickFlowNode(sourceFlowNodeName);
    await this.clickMoveAllButtononPopup();
    await expect(this.moveTokensMessage).toBeVisible();
    await this.clickFlowNode(targetFlowNodeName);
  }

  async addTokenToFlowNode(flowNodeName: string): Promise<void> {
    await this.clickFlowNode(flowNodeName);
    await this.clickAddModificationButtononPopup();
  }

  async applyChanges(): Promise<void> {
    await this.clickApplyModificationsButton();
    await this.clickApplyButtonModificationsDialog();
  }

  async getBadgeLocatorForModificationOverlay(elementLocator: Locator) {
    const badgeLocator = elementLocator.getByTestId(/^badge-/);
    return await badgeLocator.getAttribute('data-testid');
  }

  async getModificationOverlayLocatorByElementName(elementName: string) {
    return this.page
      .locator(`[data-container-id="${elementName}"]`)
      .getByTestId('modifications-overlay');
  }

  async verifyModificationOverlay(
    flowNodeName: string,
    tokenChange: number,
  ): Promise<void> {
    const flowNodeModificationOverlay =
      await this.getModificationOverlayLocatorByElementName(flowNodeName);
    await expect(flowNodeModificationOverlay).toBeVisible();

    const flowNodeModificationOverlayText =
      await flowNodeModificationOverlay.innerText();
    const flowNodeModificationOverlayBadgeValue =
      await this.getBadgeLocatorForModificationOverlay(
        flowNodeModificationOverlay,
      );

    if (tokenChange < 0) {
      tokenChange = -tokenChange;
      expect(flowNodeModificationOverlayBadgeValue).toContain('minus');
    } else {
      expect(flowNodeModificationOverlayBadgeValue).toContain('plus');
    }

    expect(flowNodeModificationOverlayText).toContain(tokenChange.toString());
  }

  async cancelOneTokenUsingHistory(flowNodeName: string): Promise<void> {
    await this.clickFlowNode(flowNodeName);
    await expect(this.multipleInstancesAlert).toBeVisible();
    const meow = this.history
      .getByTestId(/^tree-node-/)
      .locator('[aria-current="true"]')
      .first();
    await meow.click();
    await expect(
      this.page
        .getByTestId('popover')
        .getByText('Selected running instances: 1'),
    ).toBeVisible();
    await this.clickCancelButtononPopup();
    await this.verifyModificationOverlay(flowNodeName, -1);
    await this.applyChanges();
  }

  getNewVariableNameFieldSelector = (variableIndex: string) => {
    return this.page
      .getByTestId(`variable-newVariables[${variableIndex}]`)
      .locator(`[id="newVariables[${variableIndex}].name"]`);
  };

  getNewVariableValueFieldSelector = (variableIndex: string) => {
    return this.page
      .getByTestId(`variable-newVariables[${variableIndex}]`)
      .locator(`[id="newVariables[${variableIndex}].value"]`);
  };

  getEditVariableFieldSelector(variableName: string) {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByRole('textbox', {
        name: 'value',
      });
  }

  async undoModification() {
    await this.undoModificationButton.click();
  }

  async clickAddVariable() {
    await this.addVariableModificationButton.click();
  }

  async clickDeleteVariableModification() {
    await this.deleteVariableModificationButton.click();
  }

  async clickCancel() {
    await this.cancelButton.click();
  }

  getEditVariableModificationText(variableName: string) {
    return this.page.getByText(
      new RegExp(`edit variable "${variableName}"`, 'i'),
    );
  }

  getAddVariableModificationText(variableName: string) {
    return this.page.getByText(
      new RegExp(`add new variable "${variableName}"`, 'i'),
    );
  }

  getVariableModificationSummaryText(variableName: string, value: string) {
    return this.page.getByText(`${variableName}: ${value}`);
  }

  getDialogVariableModificationSummaryText(
    variableName: string,
    value: string,
  ) {
    return this.modalDialog.getByText(`${variableName}: ${value}`);
  }

  getDialogDeleteVariableModificationButton(index?: number) {
    const button = this.modalDialog.getByRole('button', {
      name: 'Delete variable modification',
    });
    return index !== undefined ? button.nth(index) : button;
  }

  getDialogCancelButton() {
    return this.modalDialog.getByRole('button', {name: 'Cancel'});
  }

  async clickDialogDeleteVariableModification(index?: number) {
    await this.getDialogDeleteVariableModificationButton(index).click();
  }

  async clickDialogCancel() {
    await this.getDialogCancelButton().click();
  }

  async addNewVariable(variableIndex: string, name: string, value: string) {
    await this.addVariableModificationButton.click();
    await expect(
      this.page.getByTestId(`variable-newVariables[${variableIndex}]`),
    ).toBeVisible();

    await this.getNewVariableNameFieldSelector(variableIndex).clear();
    await this.getNewVariableNameFieldSelector(variableIndex).type(name);
    await this.page.keyboard.press('Tab');

    await this.getNewVariableValueFieldSelector(variableIndex).type(value);
    await this.page.keyboard.press('Tab');
  }

  async editVariableValue(variableName: string, value: string) {
    await this.getEditVariableFieldSelector(variableName).clear();
    await this.getEditVariableFieldSelector(variableName).type(value);
    await this.page.keyboard.press('Tab');
  }

  async applyModifications(): Promise<void> {
    await expect(this.applyModificationsButton).toBeEnabled();
    await this.applyModificationsButton.click();
    await this.applyButtonModificationsDialog.click();
  }

  async checkNewVariableErrorMessageText(
    variableIndex: number,
    message:
      | 'Name should be unique'
      | 'Value has to be filled'
      | 'Name has to be filled'
      | 'Value has to be JSON',
    field: 'name' | 'value',
  ) {
    const errorMessage =
      field === 'name'
        ? this.newVariableByIndex(variableIndex).nameErrorMessage
        : this.newVariableByIndex(variableIndex).valueErrorMessage;
    await expect(errorMessage!).toHaveText(message);
  }

  async deleteNewVariableModification(variableIndex: number) {
    await this.newVariableByIndex(variableIndex).deleteButton.click();
  }

  async editNewVariableJSONInModal(variableIndex: number, json: string) {
    await this.newVariableByIndex(variableIndex)
      .value.clear();
    await this.newVariableByIndex(variableIndex).jsonEditorButton.click();
    const jsonEditorModal =
      this.newVariableByIndex(variableIndex).jsonEditorModal;
    await expect(jsonEditorModal.header).toBeVisible();
    await expect(jsonEditorModal.inputField).toBeVisible();
    await expect(jsonEditorModal.inputField).toBeEnabled();
    await this.fillMonacoEditor(jsonEditorModal.inputField, json);
    await jsonEditorModal.applyButton.click();
    await this.page.keyboard.press('Tab');
  }

  async editExistingVariableJSONInModal(variableName: string, json: string) {
    await this.editableExistingVariableByName(variableName).value.clear();
    await this.editableExistingVariableByName(
      variableName,
    ).jsonEditorButton.click();
    const jsonEditorModal =
      this.editableExistingVariableByName(variableName).jsonEditorModal;
    await expect(jsonEditorModal.header).toBeVisible();
    await expect(jsonEditorModal.inputField).toBeVisible();
    await expect(jsonEditorModal.inputField).toBeEnabled();
    await this.fillMonacoEditor(jsonEditorModal.inputField, json);
    await jsonEditorModal.applyButton.click();
    await this.editableExistingVariableByName(
      variableName,
    ).value.click();
    await this.page.keyboard.press('Tab');
  }

  async fillMonacoEditor(editor: Locator, value: string) {
    await this.page.keyboard.type(value, {delay: 0});
  }
}
