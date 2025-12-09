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
  readonly incidentsTable: Locator;
  readonly incidentsTableOperationSpinner: Locator;
  readonly incidentsTableRows: Locator;
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
  readonly editVariableButtonInList: Locator;
  readonly variableValueInput: Locator;
  readonly variableAddedBanner: Locator;
  readonly migratedTag: Locator;
  readonly continueButton: Locator;
  readonly applyModificationsButton: Locator;
  readonly applyButton: Locator;
  readonly addSingleFlowNodeInstanceButton: Locator;
  readonly moveSelectedInstanceButton: Locator;
  readonly executionListenerText: Locator;
  readonly taskListenerText: Locator;
  readonly stateOverlayActive: Locator;
  readonly stateOverlayCompletedEndEvents: Locator;
  readonly cancelInstanceButton: (instanceId: string) => Locator;
  readonly incidentBannerButton: (count: number) => Locator;
  readonly incidentTypeFilter: Locator;
  readonly executionCountToggle: Locator;
  readonly endDateField: Locator;

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
    this.incidentsTable = page.getByTestId('data-list');
    this.incidentsTableOperationSpinner =
      this.incidentsTable.getByTestId('operation-spinner');
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
    this.continueButton = page.getByRole('button', {name: 'Continue'});
    this.applyModificationsButton = page.getByRole('button', {
      name: 'Apply Modifications',
    });
    this.applyButton = page.getByRole('button', {name: 'Apply', exact: true});
    this.addSingleFlowNodeInstanceButton = page.getByRole('button', {
      name: 'Add single flow node instance',
    });
    this.moveSelectedInstanceButton = page.getByRole('button', {
      name: 'Move selected instance in this flow node to another target',
    });
    this.executionListenerText = page.getByText('Execution listener');
    this.taskListenerText = page.getByText('Task listener');
    this.stateOverlayActive = page.getByTestId('state-overlay-active');
    this.stateOverlayCompletedEndEvents = page.getByTestId(
      'state-overlay-completedEndEvents',
    );
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
    this.endDateField = this.instanceHeader.getByTestId('end-date');
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

  getExecutionListenerText(exact = false): Locator {
    return this.page.getByText('Execution listener', {exact});
  }

  getTaskListenerText(exact = false): Locator {
    return this.page.getByText('Task listener', {exact});
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
    await this.continueButton.click();
  }

  async applyModifications(): Promise<void> {
    await expect(this.applyModificationsButton).toBeEnabled({timeout: 10000});

    await this.applyModificationsButton.click();
    await this.applyButton.click();
  }

  getTreeItem(name: string | RegExp, exact?: boolean): Locator {
    return this.page.getByRole('treeitem', {name, exact});
  }

  async clickTreeItem(name: string | RegExp, exact?: boolean): Promise<void> {
    await this.getTreeItem(name, exact).click();
  }

  getIncidentRow(incidentType: string | RegExp): Locator {
    return this.incidentsTable.getByRole('row', {name: incidentType});
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
    if (await this.executionCountToggle.isVisible()) {
      await this.executionCountToggle.click({force: true});
    }
  }

  getDiagramElement(elementId: string): Locator {
    return this.diagram.locator(`[data-element-id="${elementId}"]`);
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
}

export {OperateProcessInstancePage};
