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

  constructor(page: Page) {
    this.page = page;
    this.modifyModeHeader = page.getByText(
      'Process Instance Modification Mode',
    );
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

  /**
   *
   * @param flowNodeName flow node to verify overlay
   * @param tokenChange expected change value to verify on the overlay
   */
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

  /**
   * This function always selects the first instance in the history tree
   * @param flowNodeName - flow node to cancel token from
   */
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
}
