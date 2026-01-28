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
  readonly moveButton: Locator;
  readonly moveModificationModal: {
    continueButton: Locator;
  };
  readonly undoButton: Locator;
  readonly applyModificationButton: Locator;
  readonly applyModificationsModal: {
    title: Locator;
    applyButton: Locator;
  };
  readonly exitModificationModeModal: {
    dialog: Locator;
    cancelButton: Locator;
    exitButton: Locator;
    subtitle: Locator;
  };
  readonly batchModificationModeText: Locator;
  readonly modificationNotification: Locator;

  constructor(page: Page) {
    this.page = page;

    this.moveButton = page.getByRole('button', {name: 'Move', exact: true});

    this.moveModificationModal = {
      continueButton: page
        .getByRole('dialog')
        .getByRole('button', {name: /continue/i}),
    };

    this.undoButton = page.getByRole('button', {name: /undo/i});

    this.applyModificationButton = page.getByRole('button', {
      name: /apply modification/i,
    });

    this.applyModificationsModal = {
      title: page.getByRole('heading', {name: /apply modifications/i}),
      applyButton: page.getByRole('button', {name: /^apply$/i}),
    };

    this.exitModificationModeModal = {
      dialog: page.getByRole('dialog', {
        name: /exit batch modification mode/i,
      }),
      cancelButton: page
        .getByRole('dialog', {name: /exit batch modification mode/i})
        .getByRole('button', {name: /cancel/i}),
      exitButton: page
        .getByRole('dialog', {name: /exit batch modification mode/i})
        .getByRole('button', {name: /exit/i}),
      subtitle: page
        .getByRole('dialog', {name: /exit batch modification mode/i})
        .getByText(/about to discard all added modifications/i),
    };

    this.batchModificationModeText = page.getByText('Batch Modification Mode', {
      exact: true,
    });

    this.modificationNotification = page.locator(
      '.cds--inline-notification__subtitle',
    );
  }

  async clickMoveButton(): Promise<void> {
    await this.moveButton.click();
  }

  async confirmMoveModification(): Promise<void> {
    await this.moveModificationModal.continueButton.click();
  }

  async clickUndoButton(): Promise<void> {
    await this.undoButton.click();
  }

  async clickApplyModificationButton(): Promise<void> {
    await this.applyModificationButton.click();
  }

  async confirmApplyModifications(): Promise<void> {
    await this.applyModificationsModal.applyButton.click();
  }

  async cancelExitModificationMode(): Promise<void> {
    await this.exitModificationModeModal.cancelButton.click();
  }

  async confirmExitModificationMode(): Promise<void> {
    await this.exitModificationModeModal.exitButton.click();
  }

  async expectModificationNotification(
    instanceCount: number,
    sourceFlowNode: string,
    targetFlowNode: string,
  ): Promise<void> {
    await expect(
      this.modificationNotification.filter({
        hasText: 'Modification scheduled',
      }),
    ).toBeVisible({timeout: 30000});

    await expect(this.modificationNotification).toContainText(
      `Move ${instanceCount} instances`,
    );
    await expect(this.modificationNotification).toContainText(sourceFlowNode);
    await expect(this.modificationNotification).toContainText(targetFlowNode);
  }

  async startBatchMoveModification(): Promise<void> {
    await this.clickMoveButton();
    await this.confirmMoveModification();
    await expect(this.batchModificationModeText).toBeVisible();
  }

  async applyAndConfirmModification(): Promise<void> {
    await expect(this.applyModificationButton).toBeVisible();
    await this.clickApplyModificationButton();
    await expect(this.applyModificationsModal.title).toBeVisible();
    await this.confirmApplyModifications();
  }
}
