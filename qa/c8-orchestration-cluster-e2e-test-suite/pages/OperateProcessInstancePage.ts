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
  readonly startFlowNode: Locator;
  readonly serviceTaskBFlowNode: Locator;
  readonly executionListenerText: Locator;
  readonly metadataModal: Locator;
  readonly modifyInstanceButton: Locator;
  readonly listenerTypeFilter: Locator;
  readonly editVariableButton: Locator;
  readonly variableValueInput: Locator;
  readonly variableAddedBanner: Locator;
  readonly migratedTag: Locator;
  readonly instanceHeaderSkeleton: Locator;
  readonly processInstanceKeyCell: Locator;
  readonly viewAllCalledInstancesLink: Locator;
  readonly viewParentInstanceLink: Locator;
  readonly drilldownButton: Locator;
  readonly canceledIcon: Locator;
  readonly editVariableButtonInList: Locator;
  readonly incidentsTableOperationSpinner: Locator;
  readonly incidentsTableRows: Locator;
  readonly incidentIconsInHistory: Locator;
  readonly rootProcessNode: Locator;
  readonly applyButton: Locator;
  readonly stateOverlayActive: Locator;
  readonly stateOverlayCompletedEndEvents: Locator;
  readonly cancelInstanceButton: (instanceId: string) => Locator;
  readonly incidentBannerButton: (count: number) => Locator;
  readonly incidentTypeFilter: Locator;
  readonly executionCountToggle: Locator;
  readonly executionCountToggleButton: Locator;
  readonly modifyDialog: Locator;
  readonly modifyDialogContinueButton: Locator;
  readonly endDateField: Locator;
  readonly incidentsViewHeader: Locator;
  readonly viewAllChildProcessesLink: Locator;
  readonly variableCellByName: (name: string | RegExp) => Locator;
  readonly getVariableRow: (variableName: string) => Locator;
  readonly getEditVariableFieldSelector: (variableName: string) => Locator;
  readonly getNewVariableNameFieldSelector: (variableName: string) => Locator;
  readonly getNewVariableValueFieldSelector: (variableName: string) => Locator;
  readonly getLastModificationLabel: (pattern: string | RegExp) => Locator;
  readonly getModificationSummaryItem: (text: string | RegExp) => Locator;
  readonly getNthDeleteVariableModificationButtonInSummary: (
    index: number,
  ) => Locator;
  readonly modificationModeText: Locator;
  readonly lastModificationFooter: Locator;
  readonly noVariablesText: Locator;
  readonly applyModificationsButton: Locator;
  readonly deleteVariableModificationButton: Locator;
  readonly cancelModificationSummaryButton: Locator;
  readonly undoButton: Locator;

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
    this.variableSpinner = page.getByTestId('variable-operation-spinner');
    this.operationSpinner = page.getByTestId('operation-spinner');
    this.executionCountToggleOn = this.instanceHistory.getByLabel(
      'show execution count',
    );
    this.executionCountToggleOff = this.instanceHistory.getByLabel(
      'hide execution count',
    );
    this.listenersTabButton = page.getByTestId('listeners-tab-button');
    this.startFlowNode = page.getByLabel('StartEvent_1');
    this.serviceTaskBFlowNode = page.getByLabel(/service task b/i);
    this.executionListenerText = page.getByText('Execution listener');
    this.metadataModal = this.page.getByRole('dialog', {name: 'metadata'});
    this.modifyInstanceButton = page.getByTestId('enter-modification-mode');
    this.listenerTypeFilter = page.getByTestId('listener-type-filter');
    this.variableAddedBanner = this.page.getByText('Variable added');
    this.migratedTag = page.locator('.cds--tag.cds--tag--green', {
      hasText: /^Migrated/,
    });
    this.instanceHeaderSkeleton = page.getByTestId('instance-header-skeleton');
    this.processInstanceKeyCell = page
      .getByTestId('instance-header')
      .locator('td')
      .nth(1);
    this.viewAllCalledInstancesLink = page.getByRole('link', {
      name: /view all called instances/i,
    });
    this.variableCellByName = (name) =>
      this.variablesList.getByRole('cell', {name});
    this.viewParentInstanceLink = this.instanceHeader.getByRole('link', {
      name: /view parent instance/i,
    });
    this.drilldownButton = page.locator('.bjs-drilldown');
    this.canceledIcon = page
      .getByTestId('instance-header')
      .getByTestId('CANCELED-icon');
    this.editVariableButtonInList = this.variablesList.getByRole('button', {
      name: /edit variable/i,
    });
    this.rootProcessNode = this.instanceHistory
      .locator('[data-testid^="node-details-"]')
      .first();
    this.incidentsTableOperationSpinner =
      this.incidentsTable.getByTestId('operation-spinner');
    this.incidentIconsInHistory = this.instanceHistory
      .getByRole('treeitem')
      .getByTestId('INCIDENT-icon');
    this.incidentsTableRows = this.incidentsTable.getByRole('row');
    this.applyButton = page.getByRole('button', {name: 'Apply', exact: true});
    this.stateOverlayActive = page.getByTestId('state-overlay-active');
    this.stateOverlayCompletedEndEvents = page.getByTestId(
      'state-overlay-completedEndEvents',
    );
    this.modifyDialog = this.page.getByLabel(
      'Process Instance Modification Mode',
    );
    this.modifyDialogContinueButton = this.modifyDialog.getByRole('button', {
      name: 'Continue',
    });
    this.cancelInstanceButton = (instanceId: string) =>
      page.getByRole('button', {name: `Cancel Instance ${instanceId}`});
    this.incidentBannerButton = (count: number) =>
      page.getByRole('button', {
        name: new RegExp(`view ${count} incidents in instance`, 'i'),
      });
    this.incidentTypeFilter = page.getByRole('combobox', {
      name: /filter by incident type/i,
    });
    this.executionCountToggle = this.instanceHistory.locator(
      '[aria-label="Show Execution Count"], [aria-label="Hide Execution Count"]',
    );
    this.executionCountToggleButton = this.page.locator(
      '#toggle-execution-count_label',
    );
    this.endDateField = this.instanceHeader.getByTestId('end-date');
    this.incidentsViewHeader = page.getByText(/incidents view/i);
    this.viewAllChildProcessesLink = this.instanceHeader.getByRole('link', {
      name: 'View all',
    });
    this.getVariableRow = (variableName: string) =>
      page.getByTestId(`variable-${variableName}`);
    this.getEditVariableFieldSelector = (variableName: string) =>
      page
        .getByTestId(`variable-${variableName}`)
        .getByRole('textbox', {name: /value/i});
    this.getNewVariableNameFieldSelector = (variableName: string) =>
      page
        .getByTestId(`variable-${variableName}`)
        .getByTestId('new-variable-name');
    this.getNewVariableValueFieldSelector = (variableName: string) =>
      page
        .getByTestId(`variable-${variableName}`)
        .getByTestId('new-variable-value');
    this.getLastModificationLabel = (pattern: string | RegExp) =>
      page.getByText(pattern);
    this.getModificationSummaryItem = (text: string | RegExp) =>
      page.getByRole('dialog').getByText(text);
    this.getNthDeleteVariableModificationButtonInSummary = (index: number) =>
      page
        .getByRole('dialog')
        .getByRole('button', {name: 'Delete variable modification'})
        .nth(index);
    this.modificationModeText = page.getByText(
      'Process Instance Modification Mode',
    );
    this.lastModificationFooter = page.getByText('Last added modification:');
    this.noVariablesText = page.getByText(/The Flow Node has no Variables/i);
    this.applyModificationsButton = page.getByRole('button', {
      name: /apply modifications/i,
    });
    this.deleteVariableModificationButton = page.getByRole('button', {
      name: /delete variable modification/i,
    });
    this.cancelModificationSummaryButton = page
      .getByRole('dialog')
      .getByRole('button', {name: 'Cancel'});
    this.undoButton = page.getByRole('button', {name: /undo/i});
  }

  async connectorResultVariableName(name: string): Promise<Locator> {
    return this.page.getByTestId(name);
  }

  async connectorResultVariableValue(variableName: string): Promise<Locator> {
    return this.page.getByTestId(variableName).locator('td').last();
  }

  async navigateToProcessInstance(id: string): Promise<void> {
    const base =
      process.env.CORE_APPLICATION_OPERATE_URL ?? 'http://localhost:8081';
    await this.page.goto(`${base}/operate/processes/${id}`);
  }

  async clickViewAllCalledInstances(): Promise<void> {
    await this.viewAllCalledInstancesLink.click();
  }

  async clickViewParentInstance(): Promise<void> {
    await this.viewParentInstanceLink.click();
  }

  async getProcessInstanceKey(): Promise<string> {
    const url = this.page.url();
    const matches = url.match(/processes\/(\d+)/);
    return matches ? matches[1] : '';
  }

  async clickEditVariableButton(variableName: string): Promise<void> {
    await this.page
      .getByTestId(`variable-${variableName}`)
      .getByRole('button', {
        name: /edit variable/i,
      })
      .click();
  }

  async clickVariableValueInput(): Promise<void> {
    await this.editVariableValueField.click();
  }

  async clearVariableValueInput(): Promise<void> {
    await this.editVariableValueField.clear();
  }

  async fillVariableValueInput(value: string): Promise<void> {
    await this.editVariableValueField.fill(value);
  }

  async clickAddVariableButton(): Promise<void> {
    await this.addVariableButton.click();
  }

  async fillNewVariable(name: string, value: string): Promise<void> {
    await this.newVariableNameField.fill(name);
    await this.newVariableValueField.fill(value);
  }

  async clickSaveVariableButton(): Promise<void> {
    await this.saveVariableButton.click();
  }

  async completedIconAssertion(): Promise<void> {
    let retryCount = 0;
    const maxRetries = 3;
    while (retryCount < maxRetries) {
      try {
        await expect(this.completedIcon).toBeVisible({
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

  getDiagramElement(elementId: string): Locator {
    return this.diagram.locator(`[data-element-id="${elementId}"]`);
  }

  async clickDiagramElement(elementId: string): Promise<void> {
    await this.getDiagramElement(elementId).click();
  }

  getTreeItem(name: string | RegExp, exact?: boolean): Locator {
    return this.page.getByRole('treeitem', {name, exact});
  }

  async clickTreeItem(name: string | RegExp, exact?: boolean): Promise<void> {
    await this.getTreeItem(name, exact).click();
  }

  getIncidentRow(incidentType: string | RegExp, selected?: boolean): Locator {
    return this.incidentsTable.getByRole('row', {
      name: incidentType,
      ...(selected !== undefined && {selected}),
    });
  }

  getSelectedIncidentRow(incidentType: string | RegExp): Locator {
    return this.getIncidentRow(incidentType, true);
  }

  getSelectedTreeItemsInHistory(name: string | RegExp): Locator {
    return this.instanceHistory.getByRole('treeitem', {name, selected: true});
  }

  findTreeItemInHistory(name: string | RegExp): Locator {
    return this.instanceHistory.getByRole('treeitem', {name});
  }

  async expandTreeItemInHistory(name: string | RegExp): Promise<void> {
    await this.instanceHistory
      .getByRole('treeitem', {name})
      .first()
      .click();
    await this.page.keyboard.press('ArrowRight');
  }

  async gotoProcessInstancePage(options: {id: string}): Promise<void> {
    await this.navigateToProcessInstance(options.id);
  }

  getIncidentRowOperationSpinner(incidentType: string | RegExp): Locator {
    return this.getIncidentRow(incidentType).getByTestId('operation-spinner');
  }

  async retryIncident(incidentType: string | RegExp): Promise<void> {
    await this.getIncidentRow(incidentType)
      .getByRole('button', {name: 'Retry Incident'})
      .click();
  }

  async cancelInstance(instanceId: string): Promise<void> {
    await this.cancelInstanceButton(instanceId).click();
    await this.applyButton.click();
  }

  async clickIncidentBanner(count: number): Promise<void> {
    await this.incidentBannerButton(count).click();
  }

  async toggleExecutionCount(): Promise<void> {
    await this.executionCountToggleButton.click();
  }

  async undoModification(): Promise<void> {
    await this.undoButton.click();
  }

  async enterModificationMode(): Promise<void> {
    await this.modifyInstanceButton.click();
  }

  async clickApplyModificationsButton(): Promise<void> {
    await this.applyModificationsButton.click();
  }

  async clickDeleteVariableModification(): Promise<void> {
    await this.deleteVariableModificationButton.click();
  }

  async cancelModificationSummary(): Promise<void> {
    await this.cancelModificationSummaryButton.click();
  }

  async clickAddVariableInModificationMode(): Promise<void> {
    await this.addVariableButton.click();
  }

  async pressTab(): Promise<void> {
    await this.page.keyboard.press('Tab');
  }

  async assertAndEnterModificationMode(): Promise<void> {
    await expect(this.getVariableRow('foo')).toBeVisible();
    await this.enterModificationMode();
    await expect(this.modificationModeText).toBeVisible();
  }

  async editExistingVariable(name: string, value: string): Promise<void> {
    await this.getEditVariableFieldSelector(name).clear();
    await this.getEditVariableFieldSelector(name).fill(value);
    await this.pressTab();
  }

  async assertLastModificationIs(label: string | RegExp): Promise<void> {
    await expect(this.lastModificationFooter).toBeVisible();
    await expect(this.getLastModificationLabel(label)).toBeVisible();
  }

  async addNewVariableInModificationMode(
    rowKey: string,
    name: string,
    value: string,
  ): Promise<void> {
    await this.clickAddVariableInModificationMode();
    await expect(this.getVariableRow(rowKey)).toBeVisible();
    await this.getNewVariableNameFieldSelector(rowKey).clear();
    await this.getNewVariableNameFieldSelector(rowKey).fill(name);
    await this.pressTab();
    await this.getNewVariableValueFieldSelector(rowKey).fill(value);
    await this.pressTab();
  }

  async verifyExecutionCountBadgesNotVisible(
    elementIds: string[],
  ): Promise<void> {
    for (const elementId of elementIds) {
      await expect(
        this.diagram.locator(`[data-element-id="${elementId}"] .badge`),
      ).toHaveCount(0);
    }
  }

  async verifyExecutionCountBadgesVisible(elementIds: string[]): Promise<void> {
    for (const elementId of elementIds) {
      await expect(this.getDiagramElement(elementId)).toBeVisible();
    }
  }
}

export {OperateProcessInstancePage};
