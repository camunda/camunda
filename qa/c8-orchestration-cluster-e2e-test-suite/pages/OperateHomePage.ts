/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

class OperateHomePage {
  private page: Page;
  readonly operateBanner: Locator;
  readonly dashboardLink: Locator;
  readonly processesTab: Locator;
  readonly decisionsTab: Locator;
  readonly informationDialog: Locator;
  readonly editVariableButton: Locator;
  readonly variableValueInput: Locator;
  readonly saveVariableButton: Locator;
  readonly editVariableSpinner: Locator;
  readonly settingsButton: Locator;
  readonly logoutButton: Locator;
  readonly openButton: Locator;
  readonly closeButton: Locator;
  readonly messageBanner: Locator;
  readonly applyButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.operateBanner = page.getByRole('link', {name: 'Camunda logo Operate'});
    this.dashboardLink = page.getByRole('link', {name: 'Dashboard'});
    this.processesTab = page.getByRole('link', {name: 'Processes'}).first();
    this.decisionsTab = page.getByRole('link', {name: 'Decisions'});
    this.informationDialog = page.getByRole('button', {
      name: 'Close this dialog',
    });
    this.editVariableButton = page.getByTestId('edit-variable-button');
    this.variableValueInput = page.getByTestId('edit-variable-value');
    this.saveVariableButton = page.getByLabel('Save variable');
    this.editVariableSpinner = page
      .getByTestId('variable-operation-spinner')
      .locator('circle')
      .nth(1);
    this.settingsButton = page.getByRole('button', {name: 'Open Settings'});
    this.logoutButton = page.getByRole('button', {name: 'Log out'});
    this.openButton = page
      .getByTestId('variable-testVariable')
      .getByLabel('Open');
    this.closeButton = page.getByRole('button', {name: 'Got it - Dismiss'});
    this.messageBanner = page.getByRole('button', {name: 'Close'});
    this.applyButton = page.getByRole('button', {name: 'Apply'});
  }

  async clickProcessesTab(): Promise<void> {
    await this.processesTab.click();
  }

  async clickDashboardLink(): Promise<void> {
    await this.dashboardLink.click();
  }

  async clickDecisionsTab(): Promise<void> {
    await this.decisionsTab.click();
  }

  async clickEditVariableButton(variableName: string): Promise<void> {
    const editVariableButton = 'variable-' + variableName;
    await this.page.getByTestId(editVariableButton).getByLabel('Edit').click();
  }

  async clickVariableValueInput(): Promise<void> {
    await this.variableValueInput.click();
  }

  async clearVariableValueInput(): Promise<void> {
    await this.variableValueInput.clear();
  }

  async fillVariableValueInput(newValue: string): Promise<void> {
    await this.openButton.click();
    await this.page
      .getByLabel('Edit Variable "testVariable"')
      .getByText('"testValue"')
      .dblclick();
    await this.page.keyboard.press('Backspace');
    await this.page.keyboard.type(newValue);
  }

  async clickSaveVariableButton(): Promise<void> {
   await this.applyButton.click();
  }

  async logout(): Promise<void> {
    await this.settingsButton.click();
    await this.logoutButton.click();
  }

  async clickMessageBanner(): Promise<void> {
    try {
      const button = this.messageBanner.or(this.closeButton).first();
      await expect(button).toBeVisible({timeout: 15000});
      await button.click();
    } catch {
      console.log('No banner or close button found to click');
    }
  }
}

export {OperateHomePage};
