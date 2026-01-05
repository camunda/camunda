/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from '../utils/waitForAssertion';

class OperateProcessInstancePage {
  private page: Page;
  readonly diagram: Locator;
  readonly drilldownButton: Locator;
  readonly completedIcon: Locator;
  readonly terminatedIcon: Locator;
  readonly diagramSpinner: Locator;
  readonly activeIcon: Locator;
  readonly variablesList: Locator;
  readonly messageVariable: Locator;
  readonly statusVariable: Locator;
  readonly instanceHeader: Locator;
  readonly instanceHistory: Locator;
  readonly rootProcessNode: Locator;
  readonly incidentsTable: Locator;
  readonly incidentsTableOperationSpinner: Locator;
  readonly incidentsTableRows: Locator;
  readonly incidentsBanner: Locator;
  readonly variablePanelEmptyText: Locator;
  readonly incidentIconsInHistory: Locator;
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
  readonly editVariableButtonInList: Locator;
  readonly variableValueInput: Locator;
  readonly variableAddedBanner: Locator;
  readonly migratedTag: Locator;
  readonly applyButton: Locator;
  readonly executionListenerText: Locator;
  readonly taskListenerText: Locator;
  readonly stateOverlayActive: Locator;
  readonly stateOverlayCompletedEndEvents: Locator;
  readonly cancelInstanceButton: (instanceId: string) => Locator;
  readonly incidentBannerButton: (count: number) => Locator;
  readonly incidentTypeFilter: Locator;
  readonly executionCountToggle: Locator;
  readonly modifyDialog: Locator;
  readonly modifyDialogContinueButton: Locator;
  readonly executionCountToggleButton: Locator;
  readonly endDateField: Locator;
  readonly incidentsViewHeader: Locator;
  readonly variableCellByName: (name: string | RegExp) => Locator;
  readonly viewAllChildProcessesLink: Locator;
  readonly instanceHeaderSkeleton: Locator;
  readonly viewAllCalledInstancesLink: Locator;
  readonly viewParentInstanceLink: Locator;
  readonly processInstanceKeyCell: Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = page.getByTestId('diagram');
    this.drilldownButton = page.locator('.bjs-drilldown');
    this.completedIcon = page
      .getByTestId('instance-header')
      .getByTestId('COMPLETED-icon');
    this.terminatedIcon = page
      .getByTestId('instance-header')
      .getByTestId('TERMINATED-icon');
    this.diagramSpinner = page.getByTestId('diagram-spinner');
    this.activeIcon = page
      .getByTestId('instance-header')
      .getByTestId('ACTIVE-icon');
    this.variablesList = page.getByTestId('variables-list');
    this.messageVariable = page.getByTestId('variable-message');
    this.statusVariable = page.getByTestId('variable-status');
    this.editVariableButton = page.getByTestId('edit-variable-button');
    this.editVariableButtonInList = this.variablesList.getByRole('button', {
      name: /edit variable/i,
    });
    this.variableValueInput = page.getByTestId('edit-variable-value');
    this.instanceHeader = page.getByTestId('instance-header');
    this.instanceHistory = page.getByTestId('instance-history');
    this.rootProcessNode = this.instanceHistory
      .locator('[data-testid^="node-details-"]')
      .first();
    this.incidentsTable = page.getByTestId('data-list');
    this.incidentsTableOperationSpinner =
      this.incidentsTable.getByTestId('operation-spinner');
    this.incidentIconsInHistory = this.instanceHistory
      .getByRole('treeitem')
      .getByTestId('INCIDENT-icon');
    this.incidentsTableRows = this.incidentsTable.getByRole('row');
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
    this.migratedTag = page.locator('.cds--tag.cds--tag--green', {
      hasText: /^Migrated/,
    });
    this.applyButton = page.getByRole('button', {name: 'Apply', exact: true});
    this.executionListenerText = page.getByText('Execution listener');
    this.taskListenerText = page.getByText('Task listener');
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
      '[aria-label="show execution count"], [aria-label="hide execution count"]',
    );
    this.executionCountToggleButton = this.instanceHistory.locator(
      'button[role="switch"]#toggle-execution-count',
    );
    this.endDateField = this.instanceHeader.getByTestId('end-date');
    this.incidentsViewHeader = page.getByText(/incidents view/i);

    this.variableCellByName = (name) =>
      this.variablesList.getByRole('cell', {name});
    this.viewAllChildProcessesLink = this.instanceHeader.getByRole('link', {
      name: 'View all',
    });
    this.instanceHeaderSkeleton = page.getByTestId('instance-header-skeleton');
    this.viewAllCalledInstancesLink = page.getByRole('link', {
      name: /view all called instances/i,
    });
    this.viewParentInstanceLink = page.getByRole('link', {
      name: /view parent instance/i,
    });
    this.processInstanceKeyCell = this.instanceHeader
      .locator('table tbody tr td')
      .nth(1);
  }

  async connectorResultVariableName(name: string): Promise<Locator> {
    return this.page.getByTestId(name);
  }

  async connectorResultVariableValue(variableName: string): Promise<Locator> {
    return this.page.getByTestId(variableName).locator('td').last();
  }

  private async waitForIconWithRetry(
    icon: Locator,
    iconName: string,
    timeout = 90000,
  ): Promise<void> {
    let retryCount = 0;
    const maxRetries = 3;
    while (retryCount < maxRetries) {
      try {
        await expect(icon).toBeVisible({timeout});
        return;
      } catch {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);
        await this.page.reload();
        await sleep(10000);
      }
    }
    throw new Error(
      `${iconName} icon not visible after ${maxRetries} attempts.`,
    );
  }

  async completedIconAssertion(): Promise<void> {
    await this.waitForIconWithRetry(this.completedIcon, 'Completed', 120000);
  }

  async activeIconAssertion(): Promise<void> {
    await this.waitForIconWithRetry(this.activeIcon, 'Active', 90000);
  }

  getVariableTestId(variableName: string) {
    return this.page.getByTestId(`variable-${variableName}`);
  }

  getListenerTypeFilterOption = (
    option: 'Execution listeners' | 'User task listeners' | 'All listeners',
  ) => {
    return this.listenerTypeFilter.getByText(option, {exact: true});
  };

  getExecutionListenerText(exact = false): Locator {
    return this.page.getByText('Execution listener', {exact});
  }

  getTaskListenerText(exact = false): Locator {
    return this.page.getByText('Task listener', {exact});
  }

  async enterModificationMode(): Promise<void> {
    await this.clickModifyInstanceButton();
    await this.clickModifyDialogContinueButton();
  }

  async clickModifyInstanceButton(): Promise<void> {
    await this.modifyInstanceButton.click();
  }

  async clickModifyDialogContinueButton(): Promise<void> {
    await this.modifyDialogContinueButton.click();
  }

  async getAllIncidentIconsAmountInHistory(): Promise<number> {
    return await this.incidentIconsInHistory.count();
  }

  async clickIncidentsBanner(): Promise<void> {
    await this.incidentsBanner.click();
  }

  async getIncidentRowByErrorMessage(errorMessage: string) {
    return this.incidentsTable.getByRole('row').filter({
      has: this.page
        .getByTestId('cell-errorMessage')
        .filter({hasText: errorMessage}),
    });
  }

  async retryIncidentByErrorMessage(errorMessage: string) {
    const incidentRow = await this.getIncidentRowByErrorMessage(errorMessage);
    console.log(
      await incidentRow.getByTestId('cell-flowNodeName').allInnerTexts(),
    );
    const retryButton = incidentRow.getByTestId('retry-operation');
    await retryButton.click();
  }

  async getAllInstanceHistoryNodeDetails(): Promise<Locator[]> {
    return this.instanceHistory
      .getByRole('treeitem')
      .getByTestId(/^node-details-/)
      .all();
  }

  async checkIfPresentExpandeingElementsInMainProcess(
    mainProcessName: string,
  ): Promise<number> {
    const expandingElements = this.instanceHistory
      .getByLabel(mainProcessName, {exact: true})
      .getByRole('group')
      .locator('.cds--tree-parent-node__toggle-icon');
    return await expandingElements.count();
  }

  async ensureElementExpandedInHistory(expandingElementName: string) {
    const expandingElements = await (
      await this.getNestedParentLocatorInHistory(expandingElementName)
    ).all();
    for (const element of expandingElements) {
      await expect(element).toBeVisible();
      const expandToggle = element.locator(
        '.cds--tree-parent-node__toggle-icon',
      );
      await expect(expandToggle).toBeVisible();
      const isExpanded = await element.getAttribute('aria-expanded');
      expect(isExpanded).not.toBeNull();

      if (isExpanded === 'false') {
        await expandToggle.click();
      }
      await expect(element).toHaveAttribute('aria-expanded', 'true');
    }
  }

  async getNestedParentLocatorInHistory(
    parentElementName: string,
  ): Promise<Locator> {
    return this.instanceHistory
      .getByRole('group')
      .getByLabel(parentElementName, {exact: true});
  }

  async getNestedGroupInHistoryLocator(
    parentElementName: string,
  ): Promise<Locator> {
    const parentLocator =
      await this.getNestedParentLocatorInHistory(parentElementName);
    return parentLocator.getByRole('group');
  }

  async getHistoryElementsDataByName(itemName: string) {
    const allHistoryItemsLocators = await this.page
      .getByTestId(/^tree-node-/)
      .all();

    var filteredElementsData = [];
    for (const element of allHistoryItemsLocators) {
      const testId = await element.getAttribute('data-testid');
      const areaLabel = await element.getAttribute('aria-label');
      const icon = element.getByTestId(/.*-icon$/).nth(1);

      if (areaLabel?.includes(itemName)) {
        filteredElementsData.push({
          testId: testId,
          ariaLabel: areaLabel,
          icon: (await icon.getAttribute('data-testid'))?.split('-')[0],
        });
      }
    }
    return filteredElementsData;
  }

  async verifyHistoryItemsStatus(
    itemName: string,
    expectedStatus: string[],
  ): Promise<void> {
    await waitForAssertion({
      assertion: async () => {
        const filteredElementsData =
          await this.getHistoryElementsDataByName(itemName);
        expect(filteredElementsData.length).toBeGreaterThan(0);
        if (filteredElementsData.length !== expectedStatus.length) {
          throw new Error(`Number does not match expected count.`);
        }
        expect(filteredElementsData.length).toBe(expectedStatus.length);
        for (let i = 0; i < filteredElementsData.length; i++) {
          expect(filteredElementsData[i].icon).toBe(expectedStatus[i]);
        }
      },
      onFailure: async () => {
        await this.page.reload();
      },
    });
  }

  async clickFlowNode(flowNodeName: string) {
    await this.diagram.getByText(flowNodeName).first().click({timeout: 20000});
  }

  async navigateToRootScope() {
    await this.rootProcessNode.click();
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
    return (await this.processInstanceKeyCell.textContent()) ?? '';
  }

  async gotoProcessInstancePage({id}: {id: string}): Promise<void> {
    await this.page.goto(`/operate/processes/${id}`);
  }

  get diagramHelper() {
    return {
      clickFlowNode: (flowNodeName: string) => {
        return this.diagram
          .getByText(flowNodeName)
          .first()
          .click({timeout: 20000});
      },
      getFlowNode: (flowNodeName: string) => {
        return this.diagram.getByText(flowNodeName);
      },
      clickEvent: async (eventName: string) => {
        await this.diagram
          .locator(`[data-element-id="${eventName}"]`)
          .click({timeout: 20000});
      },
      moveCanvasHorizontally: async (dx: number) => {
        const boundingBox = await this.diagram.boundingBox();
        if (!boundingBox) {
          throw new Error('Diagram not found');
        }

        const startX = boundingBox.x + boundingBox.width / 2;
        const startY = boundingBox.y + 50;

        await this.page.mouse.move(startX, startY);
        await this.page.mouse.down();
        await this.page.mouse.move(startX + dx, startY);
        await this.page.mouse.up();
      },
    };
  }

  getListenerRows(listenerType?: 'execution' | 'task'): Locator {
    if (listenerType === 'execution') {
      return this.page
        .getByRole('row')
        .filter({hasText: /execution listener/i});
    }
    if (listenerType === 'task') {
      return this.page.getByRole('row').filter({hasText: /task listener/i});
    }
    return this.page.getByRole('row').filter({hasText: /listener/i});
  }

  getInstanceHistoryElement(elementText: string | RegExp): Locator {
    return this.instanceHistory.getByText(elementText);
  }

  async clickInstanceHistoryElement(
    elementText: string | RegExp,
  ): Promise<void> {
    await this.getInstanceHistoryElement(elementText).click();
  }

  async openListenersTab(): Promise<void> {
    await this.listenersTabButton.click();
  }

  async verifyListenersTabVisible(): Promise<void> {
    await expect(this.listenersTabButton).toBeVisible();
  }

  async startModificationFlow(): Promise<void> {
    await this.modifyInstanceButton.click();
  }

  getTreeItem(name: string | RegExp, exact?: boolean): Locator {
    return this.page.getByRole('treeitem', {name, exact});
  }

  getSelectedTreeItem(name: string | RegExp, exact?: boolean): Locator {
    return this.page.getByRole('treeitem', {name, exact, selected: true});
  }

  findTreeItemInHistory(name: string | RegExp, exact?: boolean): Locator {
    return this.instanceHistory.getByRole('treeitem', {name, exact});
  }

  getSelectedTreeItemsInHistory(
    name: string | RegExp,
    exact?: boolean,
  ): Locator {
    return this.instanceHistory.getByRole('treeitem', {
      name,
      exact,
      selected: true,
    });
  }

  async clickTreeItem(name: string | RegExp, exact?: boolean): Promise<void> {
    await this.getTreeItem(name, exact).click();
  }

  async expandTreeItemInHistory(
    name: string | RegExp,
    exact?: boolean,
  ): Promise<void> {
    const treeItem = this.findTreeItemInHistory(name, exact).first();
    const isExpanded = await treeItem.getAttribute('aria-expanded');

    if (isExpanded === 'false') {
      await treeItem.locator('.cds--tree-parent-node__toggle').click();
    }
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
    await this.executionCountToggleButton.waitFor({state: 'visible'});
    const isChecked =
      await this.executionCountToggleButton.getAttribute('aria-checked');

    if (isChecked === 'false') {
      await this.executionCountToggleButton.click({force: true});
      await expect(this.executionCountToggleButton).toHaveAttribute(
        'aria-checked',
        'true',
        {
          timeout: 5000,
        },
      );
    }
  }

  getDiagramElement(elementId: string): Locator {
    return this.diagram.locator(`[data-element-id="${elementId}"]`);
  }

  async clickDiagramElement(elementId: string): Promise<void> {
    await this.getDiagramElement(elementId).click();
  }

  async getDiagramElementBadge(elementId: string) {
    return this.page.$(`[data-element-id="${elementId}"] .badge`);
  }

  async verifyExecutionCountBadgesNotVisible(
    elementIds: string[],
  ): Promise<void> {
    for (const elementId of elementIds) {
      const badge = await this.getDiagramElementBadge(elementId);
      expect(badge).toBeNull();
    }
  }

  async verifyExecutionCountBadgesVisible(elementIds: string[]): Promise<void> {
    for (const elementId of elementIds) {
      await expect(this.getDiagramElement(elementId)).toBeVisible();
    }
  }

  async clickViewAllChildProcesses(): Promise<void> {
    await this.viewAllChildProcessesLink.click();
  }

  async clickViewAllCalledInstances(): Promise<void> {
    await this.viewAllCalledInstancesLink.click();
  }

  async clickViewParentInstance(): Promise<void> {
    await this.viewParentInstanceLink.click();
  }
}

export {OperateProcessInstancePage};
