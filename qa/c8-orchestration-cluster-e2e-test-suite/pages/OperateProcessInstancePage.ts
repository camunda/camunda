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
  readonly rootProcessNode: Locator;
  readonly incidentsTable: Locator;
  readonly incidentsTableOperationSpinner: Locator;
  readonly incidentsTableRows: Locator;
  readonly incidentsTab: Locator;
  readonly incidentsViewHeader: Locator;
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
  readonly detailsTabButton: Locator;
  readonly variablesTabButton: Locator;
  readonly operationsLogTabButton: Locator;
  readonly operationsLogTable: Locator;
  readonly operationsLogTableRow: Locator;
  readonly operationsLogTableOperationTypeHeader: Locator;
  readonly operationsLogTableEntityTypeHeader: Locator;
  readonly operationsLogTableEntityKeyHeader: Locator;
  readonly operationsLogTableActorHeader: Locator;
  readonly operationsLogTableSortByDateHeader: Locator;
  readonly operationsLogTableProcessInstanceCell: Locator;
  readonly metadataModal: Locator;
  readonly modifyInstanceButton: Locator;
  readonly listenerTypeFilter: Locator;
  readonly editVariableButton: Locator;
  readonly editVariableButtonInList: Locator;
  readonly variableValueInput: Locator;
  readonly variableAddedBanner: Locator;
  readonly migratedTag: Locator;
  readonly continueButton: Locator;
  readonly reviewModificationsButton: Locator;
  readonly applyButton: Locator;
  readonly addSingleFlowNodeInstanceButton: Locator;
  readonly moveSelectedInstanceButton: Locator;
  readonly executionListenerText: Locator;
  readonly taskListenerText: Locator;
  readonly stateOverlayActive: Locator;
  readonly stateOverlayCompletedEndEvents: Locator;
  readonly cancelInstanceButton: (instanceId: string) => Locator;
  readonly incidentTypeFilter: Locator;
  readonly cancellationScheduledToast: Locator;
  readonly executionCountToggle: Locator;
  readonly executionCountToggleLabel: Locator;
  readonly endDateField: Locator;
  readonly modificationModeText: Locator;
  readonly lastAddedModificationText: Locator;
  readonly undoModificationButton: Locator;
  readonly deleteVariableModificationButton: Locator;
  readonly cancelButton: Locator;
  readonly addVariableModificationButton: Locator;
  readonly modalDialog: Locator;
  readonly noVariablesText: Locator;
  readonly variableCellByName: (name: string | RegExp) => Locator;
  readonly viewAllChildProcessesLink: Locator;
  readonly instanceHeaderSkeleton: Locator;
  readonly viewAllCalledInstancesLink: Locator;
  readonly viewParentInstanceLink: Locator;
  readonly incidentIconsInHistory: Locator;
  readonly modifyDialog: Locator;
  readonly modifyDialogContinueButton: Locator;
  private variableValueCellLocator: (name: string) => Locator;
  private variableButtonsCellLocator: (name: string) => Locator;
  readonly existingVariableByName: (name: string) => {
    name: Locator;
    value: Locator;
    editVariableModal: {
      button: Locator;
      exitButton: Locator;
      saveButton: Locator;
      valueInputField: Locator;
      jsonEditorButton: Locator;
      valueErrorMessage?: Locator;
      jsonEditorModal: {
        header: Locator;
        cancelButton: Locator;
        applyButton: Locator;
        inputField: Locator;
      };
    };
  };

  constructor(page: Page) {
    this.page = page;
    this.diagram = page.getByTestId('diagram-canvas');
    this.drilldownButton = this.diagram.locator('.bjs-drilldown').first();
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
    this.incidentsTableRows = this.incidentsTable.getByRole('row');
    this.incidentsTab = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Incidents$/i});
    this.incidentsTab = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Incidents$/i});
    this.incidentIconsInHistory = this.instanceHistory
      .getByRole('treeitem')
      .getByTestId('INCIDENT-icon');
    this.variablePanelEmptyText = page.getByText(
      'to view the variables, select a single element instance in the instance history',
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
    this.listenersTabButton = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Listeners$/i});
    this.detailsTabButton = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Details$/i});
    this.variablesTabButton = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Variables$/i});
    this.operationsLogTabButton = page.getByRole('link', {
      name: /^Operations Log$/i,
    });
    this.operationsLogTable = page
      .getByTestId('data-table-container')
      .getByRole('table');
    this.operationsLogTableRow = this.operationsLogTable.getByRole('row');
    this.operationsLogTableOperationTypeHeader =
      this.operationsLogTable.getByRole('columnheader', {
        name: 'Operation Type',
      });
    this.operationsLogTableEntityTypeHeader = this.operationsLogTable.getByRole(
      'columnheader',
      {
        name: 'Entity Type',
      },
    );
    this.operationsLogTableEntityKeyHeader = this.operationsLogTable.getByRole(
      'columnheader',
      {
        name: 'Entity Key',
      },
    );
    this.operationsLogTableActorHeader = this.operationsLogTable.getByRole(
      'columnheader',
      {
        name: 'Actor',
      },
    );
    this.operationsLogTableSortByDateHeader = this.operationsLogTable.getByRole(
      'columnheader',
      {
        name: 'Sort by Date',
      },
    );
    this.operationsLogTableProcessInstanceCell =
      this.operationsLogTable.getByRole('cell', {
        name: /process instance/i,
      });
    this.metadataModal = this.page.getByRole('dialog', {name: 'metadata'});
    this.modifyInstanceButton = this.instanceHeader.getByRole('button', {
      name: /modify instance/i,
    });
    this.modifyDialog = this.page.getByLabel(
      'Process Instance Modification Mode',
    );
    this.modifyDialogContinueButton = this.modifyDialog.getByRole('button', {
      name: 'Continue',
    });
    this.listenerTypeFilter = page.getByTestId('listener-type-filter');
    this.variableAddedBanner = this.page.getByText('Variable added');
    this.migratedTag = page.locator('.cds--tag.cds--tag--green', {
      hasText: /^Migrated/,
    });
    this.continueButton = page.getByRole('button', {name: 'Continue'});
    this.reviewModificationsButton = page.getByRole('button', {
      name: 'Review Modifications',
    });
    this.applyButton = page.getByRole('button', {name: 'Apply', exact: true});
    this.addSingleFlowNodeInstanceButton = page.getByRole('button', {
      name: 'Add single element instance',
    });
    this.moveSelectedInstanceButton = page.getByRole('button', {
      name: 'Move selected instance in this element to another target',
    });
    this.executionListenerText = page.getByText('Execution listener');
    this.taskListenerText = page.getByText('Task listener');
    this.stateOverlayActive = page.getByTestId('state-overlay-active');
    this.stateOverlayCompletedEndEvents = page.getByTestId(
      'state-overlay-completedEndEvents',
    );
    this.cancelInstanceButton = (instanceId: string) =>
      page.getByRole('button', {name: `Cancel Instance ${instanceId}`});
    this.incidentsViewHeader = page.getByRole('heading', {
      name: /^\d+\s+results?$/i,
    });
    this.incidentTypeFilter = page.getByRole('combobox', {
      name: /filter by incident type/i,
    });
    this.cancellationScheduledToast = page
      .getByRole('status')
      .getByText('Instance is scheduled for cancellation');
    this.endDateField = this.instanceHeader.getByTestId('end-date');
    this.incidentsViewHeader = page.getByRole('heading', {
      name: /\d+ results?/i,
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
    this.noVariablesText = page.getByText(/The element has no variables/i);

    this.variableCellByName = (name) =>
      this.variablesList.getByRole('cell', {name});
    this.viewAllChildProcessesLink = this.instanceHeader.getByRole('link', {
      name: 'View all',
    });
    this.instanceHeaderSkeleton = page.getByTestId('instance-header-skeleton');
    this.viewAllCalledInstancesLink = page.getByRole('link', {
      name: /View Called Process instance \d+/i,
    });
    this.viewParentInstanceLink = page
      .getByLabel('Breadcrumb')
      .locator('.cds--breadcrumb-item:not(.cds--breadcrumb-item--current)')
      .getByRole('link');
    this.executionCountToggle = this.page.locator('#toggle-execution-count');
    this.executionCountToggleLabel = this.page.locator(
      '#toggle-execution-count_label',
    );
    this.variableValueCellLocator = (name: string) =>
      this.variablesList
        .getByTestId(`variable-${name}`)
        .getByRole('cell')
        .nth(1);
    this.variableButtonsCellLocator = (name: string) =>
      this.variablesList
        .getByTestId(`variable-${name}`)
        .getByRole('cell')
        .nth(2);

    this.existingVariableByName = (name: string) => ({
      name: this.variablesList
        .getByTestId(`variable-${name}`)
        .getByRole('cell')
        .nth(0),
      value: this.variableValueCellLocator(name),
      editVariableModal: {
        button: this.variableButtonsCellLocator(name).getByRole('button', {
          name: `Edit variable ${name}`,
        }),
        exitButton: this.variableButtonsCellLocator(name).getByRole('button', {
          name: `Exit edit mode`,
        }),
        saveButton: this.variableButtonsCellLocator(name).getByRole('button', {
          name: `Save variable`,
        }),
        valueInputField:
          this.variableValueCellLocator(name).getByPlaceholder('Value'),
        jsonEditorButton: this.variableValueCellLocator(name).getByRole(
          'button',
          {name: `Open JSON editor`},
        ),
        valueErrorMessage: this.variableValueCellLocator(name).locator(
          '[id="value-error-msg"]',
        ),
        jsonEditorModal: {
          header: this.variableValueCellLocator(name)
            .getByRole('dialog')
            .getByRole('heading'),
          cancelButton: this.variableValueCellLocator(name)
            .getByRole('dialog')
            .getByRole('button', {name: 'Cancel'}),
          applyButton: this.variableValueCellLocator(name)
            .getByRole('dialog')
            .getByRole('button', {name: 'Apply'}),
          inputField: this.variableValueCellLocator(name)
            .getByRole('dialog')
            .getByRole('textbox'),
        },
      },
    });
  }

  async checkExistingVariableErrorMessageText(
    variableName: string,
    message:
      | 'Name should be unique'
      | 'Value has to be filled'
      | 'Name has to be filled'
      | 'Value has to be JSON',
  ) {
    const errorMessage =
      this.existingVariableByName(variableName).editVariableModal
        .valueErrorMessage;
    await expect(errorMessage!).toHaveText(message);
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

  async undoModification() {
    await this.undoModificationButton.click();
  }

  async clickReviewModifications() {
    await this.reviewModificationsButton.click();
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

  getMoveInstanceModificationText(
    sourceFlowNode: string,
    targetFlowNode: string,
  ) {
    return this.page.getByText(
      new RegExp(`move "${sourceFlowNode}" to "${targetFlowNode}"`, 'i'),
    );
  }

  async clickDialogDeleteVariableModification(index?: number) {
    await this.getDialogDeleteVariableModificationButton(index).click();
  }

  async clickDialogCancel() {
    await this.getDialogCancelButton().click();
  }

  async clickFlowNode(flowNodeName: string) {
    await this.diagram
      .locator('.djs-element[data-element-id]')
      .filter({hasText: new RegExp(`${flowNodeName}`, 'i')})
      .first()
      .click({timeout: 20000});
  }

  async navigateToRootScope() {
    await this.rootProcessNode.click();
  }

  async addNewVariableModificationMode(
    variableIndex: string,
    name: string,
    value: string,
  ) {
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

  async editVariableValueModificationMode(variableName: string, value: string) {
    await this.getEditVariableFieldSelector(variableName).clear();
    await this.getEditVariableFieldSelector(variableName).type(value);
    await this.page.keyboard.press('Tab');
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
    const table = this.page.getByTestId('instance-header').locator('table');
    const firstRow = table.locator('tbody tr').first();
    await expect(firstRow).toBeVisible();

    const headers = await table.locator('thead tr th').allTextContents();
    const keyColumnIndex = headers.findIndex((header) =>
      /process\s+instance\s+key/i.test(header.trim()),
    );

    if (keyColumnIndex !== -1) {
      const keyCell = firstRow.locator('td').nth(keyColumnIndex);
      await expect(keyCell).toBeVisible();
      return (await keyCell.textContent())?.trim() ?? '';
    }

    const firstCell = firstRow.locator('td').first();
    await expect(firstCell).toBeVisible();
    const fallbackKey = (await firstCell.textContent())?.trim() ?? '';

    if (fallbackKey) {
      return fallbackKey;
    }

    throw new Error(
      'Could not extract Process Instance Key from instance header table',
    );
  }

  async gotoProcessInstancePage({id}: {id: string}): Promise<void> {
    await this.page.goto(`/operate/processes/${id}`);
  }

  async gotoProcessInstanceOperationsLogPage({
    id,
  }: {
    id: string;
  }): Promise<void> {
    await this.gotoProcessInstancePage({id});
    await this.operationsLogTabButton.click();
  }

  get diagramHelper() {
    return {
      clickFlowNode: (flowNodeName: string) => {
        return this.diagram
          .locator('.djs-element[data-element-id]')
          .filter({hasText: new RegExp(`${flowNodeName}`, 'i')})
          .first()
          .click({timeout: 20000});
      },
      getFlowNode: (flowNodeName: string) => {
        return this.diagram
          .locator('.djs-element[data-element-id]')
          .filter({hasText: new RegExp(`${flowNodeName}`, 'i')});
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

  async clickVariablesTab(): Promise<void> {
    await this.variablesTabButton.click();
  }

  async clickDetailsTab(): Promise<void> {
    await this.detailsTabButton.click();
  }

  async verifyListenersTabVisible(): Promise<void> {
    await expect(this.listenersTabButton).toBeVisible();
  }

  async startModificationFlow(): Promise<void> {
    await this.modifyInstanceButton.click();
    await this.continueButton.click();
  }

  async applyModifications(): Promise<void> {
    await expect(this.reviewModificationsButton).toBeEnabled({timeout: 10000});

    await this.reviewModificationsButton.click();
    await this.applyButton.click();
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
    await expect(this.getTreeItem(name, exact)).toBeVisible();
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

  async navigateToFailingElementForIncident(
    incidentType: string | RegExp,
    failingElement: string,
  ): Promise<void> {
    await this.getIncidentRow(incidentType)
      .getByRole('link', {name: failingElement})
      .click();
  }

  async cancelInstance(instanceId: string): Promise<void> {
    await this.cancelInstanceButton(instanceId).click();
    await this.applyButton.click();
  }

  async toggleExecutionCount(): Promise<void> {
    await this.executionCountToggle.waitFor({state: 'visible'});
    if (
      (await this.executionCountToggle.getAttribute('aria-checked')) !== 'true'
    ) {
      await this.executionCountToggleLabel.click();
      await expect(this.executionCountToggle).toHaveAttribute(
        'aria-checked',
        'true',
      );
    }
  }

  getDiagramElement(elementId: string): Locator {
    return this.diagram.locator(`[data-element-id="${elementId}"]`);
  }

  async clickDiagramElement(elementId: string): Promise<void> {
    await this.getDiagramElement(elementId).click();
  }

  async getDiagramElementBadge(elementId: string): Promise<Locator> {
    return this.getDiagramElement(elementId).locator('.badge');
  }

  async verifyExecutionCountBadgesNotVisible(
    elementIds: string[],
  ): Promise<void> {
    for (const elementId of elementIds) {
      const badge = await this.getDiagramElementBadge(elementId);
      await expect(badge).toHaveCount(0);
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
    await this.clickDetailsTab();
    await this.viewAllCalledInstancesLink.click();
  }

  async clickViewParentInstance(): Promise<void> {
    await this.viewParentInstanceLink.click();
  }

  async clickIncidentsTab(): Promise<void> {
    await this.incidentsTab.click();
  }

  async getIncidentRowByErrorType(errorType: string) {
    const parentRows = this.incidentsTable.locator(
      'tr[data-parent-row="true"]',
    );

    const exactParentRow = parentRows.filter({
      has: this.page
        .getByTestId('cell-errorType')
        .filter({hasText: new RegExp(`^${errorType}$`, 'i')}),
    });

    if ((await exactParentRow.count()) > 0) {
      return exactParentRow.first();
    }

    const partialParentRow = parentRows.filter({
      has: this.page.getByTestId('cell-errorType').filter({hasText: errorType}),
    });

    if ((await partialParentRow.count()) > 0) {
      return partialParentRow.first();
    }

    return this.incidentsTable.getByRole('row').filter({
      has: this.page.getByTestId('cell-errorType').filter({hasText: errorType}),
    });
  }

  async retryIncidentByErrorType(errorType: string) {
    const incidentRow = await this.getIncidentRowByErrorType(errorType);
    const retryButton = incidentRow.getByTestId('retry-operation');
    await retryButton.click();
  }

  async clickModifyInstanceButton(): Promise<void> {
    await this.modifyInstanceButton.click();
  }

  async clickModifyDialogContinueButton(): Promise<void> {
    await this.modifyDialogContinueButton.click();
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
      let isExpanded = await element.getAttribute('aria-expanded');
      expect(isExpanded).not.toBeNull();
      const expandToggle = element.locator(
        '.cds--tree-parent-node__toggle-icon',
      );

      if (isExpanded === 'false') {
        await expandToggle.click();
        isExpanded = await element.getAttribute('aria-expanded');
      }

      await expect(element).toHaveAttribute('aria-expanded', 'true');
    }
  }

  async getNestedParentLocatorInHistory(
    parentElementName: string,
  ): Promise<Locator> {
    return this.instanceHistory.getByRole('treeitem', {
      name: parentElementName,
      exact: true,
    });
  }

  async getNestedGroupInHistoryLocator(
    parentElementName: string,
  ): Promise<Locator> {
    const parentLocator =
      await this.getNestedParentLocatorInHistory(parentElementName);
    return parentLocator.getByRole('group');
  }

  async enterModificationMode(): Promise<void> {
    await this.clickModifyInstanceButton();
    if (await this.modifyDialogContinueButton.isVisible()) {
      await this.clickModifyDialogContinueButton();
    }
  }

  async getHistoryElementsDataByName(itemName: string) {
    const allHistoryItemsLocators = await this.instanceHistory
      .getByRole('treeitem')
      .all();

    var filteredElementsData = [];
    for (const element of allHistoryItemsLocators) {
      const testId = await element.getAttribute('data-testid');
      const ariaLabel = await element.getAttribute('aria-label');
      const iconLocator = element.getByTestId(/.*-icon$/).nth(1);
      const iconTestId = await iconLocator.getAttribute('data-testid');

      if (!ariaLabel?.includes(itemName)) {
        continue;
      }

      filteredElementsData.push({
        testId,
        ariaLabel,
        icon: iconTestId?.split('-')[0],
      });
    }

    return filteredElementsData;
  }

  /**
   * @param itemName - The name of the history item to verify
   * @param expectedStatus - Array of icons in expected order in history, can be 'COMPLETED', 'ACTIVE', 'TERMINATED', 'INCIDENT'
   */
  async verifyHistoryItemsStatus(
    itemName: string,
    expectedStatus: string[],
  ): Promise<void> {
    const filteredElementsData =
      await this.getHistoryElementsDataByName(itemName);
    expect(filteredElementsData.length).toBeGreaterThan(0);
    if (filteredElementsData.length !== expectedStatus.length) {
      throw new Error(`Number does not match expected count.`);
    }
    expect(filteredElementsData).toHaveLength(expectedStatus.length);
    for (let i = 0; i < filteredElementsData.length; i++) {
      expect(filteredElementsData[i].icon).toBe(expectedStatus[i]);
    }
  }

  async clickOnElementInDiagram(elementId: string): Promise<void> {
    await this.clickDiagramElement(elementId);
  }

  getCalledProcessLink(processName: string): Locator {
    return this.page.getByRole('link', {name: processName});
  }

  async clickCalledProcessLink(processName: string): Promise<void> {
    await this.getCalledProcessLink(processName).click();
  }

  getOperationsLogTableRowCount(): Promise<number> {
    return this.operationsLogTableRow.count();
  }

  getOperationsLogTableProcessInstanceCellCount(): Promise<number> {
    return this.operationsLogTableProcessInstanceCell.count();
  }

  async getIncidentCount(): Promise<number> {
    await expect(this.incidentsViewHeader).toBeVisible();
    const headingText = await this.incidentsViewHeader.innerText();
    if (!headingText) {
      return 0;
    }

    const match = headingText.match(/(\d+)\s+results?/i);
    return match ? parseInt(match[1], 10) : 0;
  }

  async verifyIncidentCount(expectedCount: number): Promise<void> {
    const actualCount = await this.getIncidentCount();
    expect(actualCount).toBe(expectedCount);
  }

  async verifyIncidents(errorTypes: string[], elementId?: string) {
    if (elementId) {
      await this.clickOnElementInDiagram(elementId);
    }
    await this.clickIncidentsTab();
    await this.verifyIncidentCount(errorTypes.length);
    for (const errorType of errorTypes) {
      const incidentRow = await this.getIncidentRowByErrorType(errorType);
      await expect(incidentRow).toBeVisible({timeout: 30000});
    }
  }
}

export {OperateProcessInstancePage};
