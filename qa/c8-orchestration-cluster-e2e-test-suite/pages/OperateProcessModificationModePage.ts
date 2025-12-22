/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

export class OperateProcessModificationModePage {
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
      .locator(`[data-container-id=${elementName}]`)
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

  getNewVariableNameFieldSelector = (variableName: string) => {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByTestId('new-variable-name');
  };

  getNewVariableValueFieldSelector = (variableName: string) => {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByTestId('new-variable-value');
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
      this.page.getByTestId(`variable-${variableIndex}`),
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
}
