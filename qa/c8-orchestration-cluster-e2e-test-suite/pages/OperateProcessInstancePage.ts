/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {sleep} from 'utils/sleep';

class OperateProcessInstancePage {
  private page: Page;
  readonly diagram: Locator;
  readonly completedIcon: Locator;
  readonly diagramSpinner: Locator;
  readonly activeIcon: Locator;
  readonly variablesList: Locator;
  readonly messageVariable: Locator;
  readonly statusVariable: Locator;
  readonly instanceHeader: Locator;
  readonly instanceHistory: Locator;
  readonly incidentsTable: Locator;
  readonly incidentsBanner: Locator;
  readonly variablePanelEmptyText: Locator;
  readonly addVariableButton: Locator;
  readonly saveVariableButton: Locator;
  readonly newVariableNameField: Locator;
  readonly newVariableValueField: Locator;
  readonly editVariableValueField: Locator;
  readonly variableSpinner: Locator;
  readonly operationSpinner: Locator;
  readonly executionCountToggleOn: Locator;
  readonly executionCountToggleOff: Locator;
  readonly listenersTabButton: Locator;
  readonly metadataModal: Locator;
  readonly modifyInstanceButton: Locator;
  readonly listenerTypeFilter: Locator;
  readonly editVariableButton: Locator;
  readonly variableValueInput: Locator;
  readonly variableAddedBanner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = page.getByTestId('diagram');
    this.completedIcon = page
      .getByTestId('instance-header')
      .getByTestId('COMPLETED-icon');
    this.diagramSpinner = page.getByTestId('diagram-spinner');
    this.activeIcon = page
      .getByTestId('instance-header')
      .getByTestId('ACTIVE-icon');
    this.variablesList = page.getByTestId('variables-list');
    this.messageVariable = page.getByTestId('variable-message');
    this.statusVariable = page.getByTestId('variable-status');
    this.editVariableButton = page.getByTestId('edit-variable-button');
    this.variableValueInput = page.getByTestId('edit-variable-value');
    this.instanceHeader = page.getByTestId('instance-header');
    this.instanceHistory = page.getByTestId('instance-history');
    this.incidentsTable = page.getByTestId('data-list');
    this.incidentsBanner = page.getByTestId('incidents-banner');
    this.variablePanelEmptyText = page.getByText(
      'to view the variables, select a single flow node instance in the instance history',
    );
    this.addVariableButton = page.getByRole('button', {name: 'Add variable'});
    this.saveVariableButton = page.getByRole('button', {name: 'Save variable'});
    this.newVariableNameField = page.getByRole('textbox', {name: 'Name'});
    this.newVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.editVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.variableSpinner = page.getByTestId('full-variable-loader');
    this.operationSpinner = page.getByTestId('operation-spinner');
    this.executionCountToggleOn = this.instanceHistory.getByLabel(
      'show execution count',
    );
    this.executionCountToggleOff = this.instanceHistory.getByLabel(
      'hide execution count',
    );
    this.listenersTabButton = page.getByTestId('listeners-tab-button');
    this.metadataModal = this.page.getByRole('dialog', {name: 'metadata'});
    this.modifyInstanceButton = page.getByTestId('enter-modification-mode');
    this.listenerTypeFilter = page.getByTestId('listener-type-filter');
    this.variableAddedBanner = this.page.getByText('Variable added');
  }

  async connectorResultVariableName(name: string): Promise<Locator> {
    return this.page.getByTestId(name);
  }

  async connectorResultVariableValue(variableName: string): Promise<Locator> {
    return this.page.getByTestId(variableName).locator('td').last();
  }

  async completedIconAssertion(): Promise<void> {
    let retryCount = 0;
    const maxRetries = 3;
    while (retryCount < maxRetries) {
      try {
        await expect(this.completedIcon).toBeVisible({
          timeout: 120000,
        });
        return; // Exit the function if the expectation is met
      } catch {
        // If the completed icon isn't found, reload the page and try again
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
        await sleep(10000);
      }
    }
    throw new Error(`Completed icon not visible after ${maxRetries} attempts.`);
  }

  async activeIconAssertion(): Promise<void> {
    let retryCount = 0;
    const maxRetries = 3;
    while (retryCount < maxRetries) {
      try {
        await expect(this.activeIcon).toBeVisible({
          timeout: 90000,
        });
        return; // Exit the function if the expectation is met
      } catch {
        // If the active icon isn't found, reload the page and try again
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
        await sleep(10000);
      }
    }
    throw new Error(`Active icon not visible after ${maxRetries} attempts.`);
  }
  getEditVariableFieldSelector(variableName: string) {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByRole('textbox', {
        name: 'value',
      });
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

  getListenerTypeFilterOption = (
    option: 'Execution listeners' | 'User task listeners' | 'All listeners',
  ) => {
    return this.listenerTypeFilter.getByText(option, {exact: true});
  };

  async undoModification() {
    await this.page
      .getByRole('button', {
        name: 'undo',
      })
      .click();
  }

  async navigateToProcessInstance(id: string) {
    await this.page.goto(`operate/processes/${id}`);
  }

  async getNthTreeNodeTestId(n: number) {
    return this.page
      .getByTestId(/^tree-node-/)
      .nth(n)
      .getAttribute('data-testid');
  }
  async clickEditVariableButton(variableName: string): Promise<void> {
    const editVariableButton = 'Edit variable ' + variableName;
    await this.page.getByLabel(editVariableButton).click();
  }

  async clickVariableValueInput(): Promise<void> {
    await this.variableValueInput.click();
  }

  async clearVariableValueInput(): Promise<void> {
    await this.variableValueInput.clear();
  }

  async fillVariableValueInput(value: string): Promise<void> {
    await this.variableValueInput.fill(value);
  }

  async clickSaveVariableButton(): Promise<void> {
    await this.saveVariableButton.click();
  }

  async clickAddVariableButton(): Promise<void> {
    await this.addVariableButton.click();
  }

  async fillNewVariable(name: string, value: string): Promise<void> {
    await this.newVariableNameField.fill(name);
    await this.newVariableValueField.fill(value);
  }

  async getProcessInstanceKey(): Promise<string> {
    const processInstanceKey = this.page.locator('table tbody tr td').nth(1);
    return (await processInstanceKey.textContent()) ?? '';
  }
}

export {OperateProcessInstancePage};
