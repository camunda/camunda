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

    async addTokenToFlowNodeAndApplyChanges(): Promise<void> {
        await this.clickAddModificationButtononPopup();
        await this.clickApplyModificationsButton();
        await this.clickApplyButtonModificationsDialog();
    }
}