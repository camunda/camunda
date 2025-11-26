/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Page, Locator, expect } from '@playwright/test';

export class OperateProcessModificationModePage {
    private page: Page;
    readonly modifyModeHeader: Locator;
    readonly flowNodeModificationsPopup: Locator;
    readonly addModificationButtononPopup: Locator;
    readonly moveAllButtononPopup: Locator;
    readonly applyModificationsButton: Locator;
    readonly discardAllModificationsButton: Locator;
    readonly cancelButtonModificationDialog: Locator;
    readonly applyButtonModificationsDialog: Locator;
    readonly moveTokensMessage: Locator;
    readonly diagram: Locator;

    constructor(page: Page) {
        this.page = page;
        this.modifyModeHeader = page.getByText('Process Instance Modification Mode');
        this.flowNodeModificationsPopup = page.getByText('Flow Node Modifications');
        this.addModificationButtononPopup = page.getByTitle('Add single flow node instance');
        this.moveAllButtononPopup = page.getByTitle('Move all running instances in this flow node to another target');
        this.applyModificationsButton = page.getByTestId('apply-modifications-button');
        this.discardAllModificationsButton = page.getByTestId('discard-all-button');
        this.cancelButtonModificationDialog = page.getByRole('dialog').getByRole('button', { name: 'Cancel' });
        this.applyButtonModificationsDialog = page.getByRole('dialog').getByRole('button', { name: 'Apply' });
        this.moveTokensMessage = page.getByText('Select the target flow node in the diagram');
        this.diagram = this.page.getByTestId('diagram');
    }

    async clickAddModificationButtononPopup(): Promise<void> {
        await this.addModificationButtononPopup.click();
    }

    async clickMoveAllButtononPopup(): Promise<void> {
        await this.moveAllButtononPopup.click();
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
        return this.getFlowNode(flowNodeName).first().click({ timeout: 20000 });
    }

    clickSubProcess(subProcessName: string) {
        return this.getFlowNode(subProcessName).click({
            position: { x: 5, y: 5 },
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
        await this.clickApplyModificationsButton();
        await this.clickApplyButtonModificationsDialog();
    }

    async addTokenToSubprocessAndApplyChanges(flowNodeName: string): Promise<void> {
        await this.clickSubProcess(flowNodeName);
        await this.clickAddModificationButtononPopup();
        await this.clickApplyModificationsButton();
        await this.clickApplyButtonModificationsDialog();
    }

    async moveAllTokensFromSelectedFlowNodeToTarget(sourceFlowNodeName: string, targetFlowNodeName: string): Promise<void> {
        await this.clickFlowNode(sourceFlowNodeName);
        await this.clickMoveAllButtononPopup();
        await expect(this.moveTokensMessage).toBeVisible();
        await this.clickFlowNode(targetFlowNodeName);
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
        return this.page.locator(`[data-container-id=${elementName}]`).getByTestId('modifications-overlay');
    }

    /**
     * 
     * @param flowNodeName flow node to verify overlay
     * @param expectedChange expected change value to verify on the overlay
     */
    async verifyModificationOverlay(flowNodeName: string, expectedChange: number): Promise<void> {
        const flowNodeModificationOverlay = await this.getModificationOverlayLocatorByElementName(flowNodeName);
        await expect(flowNodeModificationOverlay).toBeVisible();

        const flowNodeModificationOverlayText = await flowNodeModificationOverlay.innerText();
        const flowNodeModificationOverlayBadgeValue = await this.getBadgeLocatorForModificationOverlay(flowNodeModificationOverlay);

        if (expectedChange < 0) {
            expectedChange = -expectedChange;
            expect(flowNodeModificationOverlayBadgeValue).toContain('minus');
        } else {
            expect(flowNodeModificationOverlayBadgeValue).toContain('plus');
        }

        expect(flowNodeModificationOverlayText).toContain(expectedChange.toString());
    }
}