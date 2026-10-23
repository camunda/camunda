/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForAssertion} from 'utils/waitForAssertion';

type InstanceHeaderAction = 'suspend' | 'resume' | 'cancel';

// The header names an operation by menu label or by button label depending on
// viewport; pairing both spellings lets callers ask for the action instead.
const INSTANCE_HEADER_ACTIONS: ReadonlyArray<{
  action: InstanceHeaderAction;
  button: RegExp;
  menuItem: RegExp;
}> = [
  {action: 'suspend', button: /^Suspend Instance \d+$/, menuItem: /^suspend$/i},
  {action: 'resume', button: /^Resume Instance \d+$/, menuItem: /^resume$/i},
  {action: 'cancel', button: /^Cancel Instance \d+$/, menuItem: /^cancel$/i},
];

class OperateProcessInstancePage {
  private page: Page;
  readonly diagram: Locator;
  readonly drilldownButton: Locator;
  readonly completedIcon: Locator;
  readonly terminatedIcon: Locator;
  readonly activeIcon: Locator;
  readonly variablesList: Locator;
  readonly instanceHeader: Locator;
  readonly drainingTag: Locator;
  readonly suspendedStateIcon: Locator;
  readonly instanceHistory: Locator;
  readonly incidentsTable: Locator;
  readonly incidentsTableOperationSpinner: Locator;
  readonly incidentsTableRows: Locator;
  readonly firstIncidentRetryButton: Locator;
  readonly incidentsTab: Locator;
  readonly variablePanelEmptyText: Locator;
  readonly addVariableButton: Locator;
  readonly saveVariableButton: Locator;
  readonly newVariableNameField: Locator;
  readonly newVariableValueField: Locator;
  readonly editVariableValueField: Locator;
  readonly executionCountToggleOn: Locator;
  readonly executionCountToggleOff: Locator;
  readonly listenersTabButton: Locator;
  readonly detailsTabButton: Locator;
  readonly jobPriorityValue: Locator;
  readonly openTasklistLink: Locator;
  readonly waitingStatus: Locator;
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
  readonly editVariableButtonInList: Locator;
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
  readonly incidentsViewHeader: Locator;
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
  readonly viewAllCalledInstancesLink: Locator;
  readonly viewParentInstanceLink: Locator;
  readonly modifyDialog: Locator;
  readonly modifyDialogContinueButton: Locator;
  readonly editButton: Locator;
  readonly openButtonLast: Locator;
  readonly openButtonFirst: Locator;
  readonly applyVariableButton: Locator;
  private variableRow: (name: string) => Locator;
  private variableValueCellLocator: (name: string) => Locator;
  private variableButtonsCellLocator: (name: string) => Locator;
  readonly existingVariableByName: (name: string) => {
    name: Locator;
    value: Locator;
    readModeValue: Locator;
    editor: Locator;
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
        copyButton: Locator;
        copiedButton: Locator;
        closeButton: Locator;
      };
    };
  };
  readonly editor: Locator;

  constructor(page: Page) {
    this.page = page;
    this.diagram = page.getByRole('region', {name: 'Diagram', exact: true});
    this.drilldownButton = this.diagram.locator('.bjs-drilldown').first();
    this.instanceHeader = page.getByRole('group', {
      name: 'Instance header',
      exact: true,
    });
    this.completedIcon = this.instanceHeader.getByRole('img', {
      name: 'completed state',
      exact: true,
    });
    this.terminatedIcon = this.instanceHeader.getByRole('img', {
      name: 'terminated state',
      exact: true,
    });
    this.activeIcon = this.instanceHeader.getByRole('img', {
      name: 'active state',
      exact: true,
    });
    this.variablesList = page.getByRole('table', {
      name: 'Variable List',
      exact: true,
    });
    this.variableRow = (name: string) => {
      const newVariableIndex = name.match(/^newVariables\[(\d+)\]$/)?.[1];
      const rows = this.variablesList.getByRole('row');
      return newVariableIndex === undefined
        ? rows.filter({has: page.getByTitle(name, {exact: true})})
        : rows
            .filter({
              has: page.getByRole('textbox', {name: 'Name', exact: true}),
            })
            .nth(Number(newVariableIndex));
    };
    this.editVariableButtonInList = this.variablesList.getByRole('button', {
      name: /edit/i,
    });
    this.drainingTag = this.instanceHeader.getByText('Draining', {exact: true});
    this.suspendedStateIcon = this.instanceHeader.getByRole('img', {
      name: 'suspended state',
      exact: true,
    });
    this.instanceHistory = page.getByRole('tree', {
      name: /instance history$/i,
    });
    this.incidentsTable = page.getByRole('table').filter({
      has: page.getByRole('columnheader', {
        name: 'Failing Element',
        exact: true,
      }),
    });
    this.incidentsTableOperationSpinner = this.incidentsTable.getByRole('img', {
      name: 'loading',
      exact: true,
    });
    this.incidentsTableRows = this.incidentsTable.getByRole('row');
    // A locator, not a click helper: the suspended case asserts it is disabled.
    this.firstIncidentRetryButton = this.incidentsTableRows
      .getByRole('button', {name: 'Retry Incident'})
      .first();
    this.incidentsTab = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Incidents$/i});
    this.variablePanelEmptyText = page.getByText(
      'to view the variables, select a single element instance in the instance history',
    );
    this.addVariableButton = page.getByRole('button', {name: 'Add variable'});
    this.saveVariableButton = page.getByRole('button', {name: 'Save'});
    this.newVariableNameField = page.getByRole('textbox', {
      name: 'Name',
      exact: true,
    });
    this.newVariableValueField = page.getByRole('group', {
      name: 'Value',
      exact: true,
    });
    this.editVariableValueField = page.getByRole('group', {
      name: 'Value',
      exact: true,
    });
    this.executionCountToggleOn = page.getByLabel('show execution count');
    this.executionCountToggleOff = page.getByLabel('hide execution count');
    this.listenersTabButton = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Listeners$/i});
    this.detailsTabButton = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Details$/i});
    this.jobPriorityValue = page
      .getByRole('table', {name: 'Element Instance Details', exact: true})
      .getByRole('row')
      .filter({
        has: page.getByRole('cell', {name: 'Job Priority', exact: true}),
      })
      .getByRole('cell')
      .nth(1);
    this.openTasklistLink = page.getByRole('link', {name: 'Open Tasklist'});
    this.waitingStatus = page.getByText(/^Waiting for /);
    this.variablesTabButton = page
      .getByLabel('Process Instance Bottom Panel Tabs')
      .getByRole('link', {name: /^Variables$/i});
    this.operationsLogTabButton = page.getByRole('link', {
      name: /^Operations Log$/i,
    });
    this.operationsLogTable = page.getByRole('table').filter({
      has: page.getByRole('columnheader', {
        name: /^(Sort by )?Operation type$/i,
      }),
    });
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
    this.listenerTypeFilter = page.getByRole('combobox', {
      name: 'Listener type',
      exact: true,
    });
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
    this.stateOverlayActive = this.diagram.getByRole('img', {
      name: /^Active(: \d+)?$/,
    });
    this.stateOverlayCompletedEndEvents = this.diagram.getByRole('img', {
      name: /^Completed(: \d+)?$/,
    });
    this.cancelInstanceButton = (instanceId: string) =>
      page.getByRole('button', {name: `Cancel Instance ${instanceId}`});
    this.incidentTypeFilter = page.getByRole('combobox', {
      name: /filter by incident type/i,
    });
    this.cancellationScheduledToast = page
      .getByRole('status')
      .getByText('Instance is scheduled for cancellation');
    this.incidentsViewHeader = page.getByRole('heading', {
      name: /^\d+\s+results?$/i,
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
      this.variableRow(name).getByRole('cell').nth(1);
    this.variableButtonsCellLocator = (name: string) =>
      this.variableRow(name).getByRole('cell').nth(2);

    this.existingVariableByName = (name: string) => ({
      name: this.variableRow(name).getByRole('cell').nth(0),
      value: this.variableValueCellLocator(name),
      readModeValue: this.variableValueCellLocator(name).getByRole('button', {
        name: `Copy ${name}`,
        exact: true,
      }),
      editor: this.variableValueCellLocator(name)
        .getByRole('group')
        .filter({has: page.getByRole('textbox')}),
      editVariableModal: {
        button: this.variableButtonsCellLocator(name).getByRole('button', {
          name: /^Edit( variable .+)?$/,
        }),
        exitButton: this.variableButtonsCellLocator(name).getByRole('button', {
          name: `Exit edit mode`,
        }),
        saveButton: this.variableButtonsCellLocator(name).getByRole('button', {
          name: `Save variable`,
        }),
        valueInputField:
          this.variableValueCellLocator(name).getByPlaceholder('Value'),
        jsonEditorButton: this.variableButtonsCellLocator(name).getByRole(
          'button',
          {name: 'Open'},
        ),
        valueErrorMessage: this.variableValueCellLocator(name).locator(
          '.cds--form-requirement',
        ),
        jsonEditorModal: {
          header: this.page.getByRole('dialog').getByRole('heading'),
          cancelButton: this.page
            .getByRole('dialog')
            .getByRole('button', {name: 'Cancel'}),
          applyButton: this.page
            .getByRole('dialog')
            .getByRole('button', {name: 'Apply'}),
          inputField: this.page
            .getByRole('dialog')
            .getByRole('code')
            .getByRole('textbox', {name: 'Editor content'}),
          copyButton: this.page
            .getByRole('dialog')
            .getByRole('button', {name: /copy/i}),
          copiedButton: this.page
            .getByRole('dialog')
            .getByRole('button', {name: /copied/i}),
          closeButton: this.page
            .getByRole('dialog')
            .getByRole('button', {name: 'Close'}),
        },
      },
    });
    this.editor = page
      .getByRole('group')
      .filter({has: page.getByRole('textbox')});
    this.openButtonLast = page.locator('[aria-label="Open variable"]').last();
    this.openButtonFirst = page.locator('[aria-label="Open variable"]').first();
    this.editButton = page.getByRole('button', {name: 'Edit'});
    this.applyVariableButton = page.getByRole('button', {name: 'Apply'});
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
    timeout: number,
  ): Promise<void> {
    await waitForAssertion({
      assertion: async () => {
        await expect(icon).toBeVisible({timeout});
      },
      onFailure: async () => {
        await this.page.reload();
      },
    });
  }

  async completedIconAssertion(): Promise<void> {
    await this.waitForIconWithRetry(this.completedIcon, 120000);
  }

  async activeIconAssertion(): Promise<void> {
    await this.waitForIconWithRetry(this.activeIcon, 90000);
  }
  getEditVariableFieldSelector(variableName: string) {
    return this.variableRow(variableName)
      .getByRole('group', {name: variableName, exact: true})
      .getByRole('textbox', {name: variableName, exact: true});
  }

  getNewVariableNameFieldSelector = (variableName: string) => {
    return this.variableRow(variableName).getByRole('textbox', {
      name: 'Name',
      exact: true,
    });
  };

  getNewVariableValueFieldSelector = (variableName: string) => {
    return this.variableRow(variableName).getByRole('group', {
      name: 'Value',
      exact: true,
    });
  };

  getVariableTestId(variableName: string) {
    return this.variableRow(variableName);
  }

  getListenerTypeFilterOption = (
    option: 'Execution listeners' | 'User task listeners' | 'All listeners',
  ) => {
    return this.page.getByRole('option', {name: option, exact: true});
  };

  getExecutionListenerText(exact = false): Locator {
    return this.page.getByText('Execution listener', {exact});
  }

  getTaskListenerText(exact = false): Locator {
    return this.page.getByText('Task listener', {exact});
  }

  async undoModification() {
    await expect(this.undoModificationButton).toBeVisible();
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
    await this.getDiagramFlowNode(flowNodeName).click({timeout: 20000});
  }

  getDiagramFlowNode(flowNodeName: string): Locator {
    return this.diagram
      .locator('.djs-element[data-element-id]')
      .filter({hasText: new RegExp(`${flowNodeName}`, 'i')})
      .first();
  }

  async selectFlowNodeWhenSelectable(flowNodeName: string): Promise<void> {
    const flowNode = this.getDiagramFlowNode(flowNodeName);
    // The diagram renders (and looks interactive) before the element-instance
    // statistics load, and BpmnJS ignores element clicks until then — it adds
    // the `op-selectable` marker only once the statistics resolve. Wait for it
    // so the click actually selects the element instead of being a silent no-op.
    await expect(flowNode).toHaveClass(/op-selectable/, {timeout: 30000});
    await flowNode.click({timeout: 20000});
  }

  async navigateToRootScope() {
    const rootNode = this.instanceHistory.getByRole('treeitem').first();
    const rootNodeName = await rootNode.getAttribute('aria-label');
    await rootNode.getByText(rootNodeName!, {exact: true}).first().click();
  }

  async addNewVariableModificationMode(
    variableIndex: string,
    name: string,
    value: string,
  ) {
    await this.addVariableModificationButton.click();
    await expect(this.variableRow(variableIndex)).toBeVisible();

    await this.getNewVariableNameFieldSelector(variableIndex).clear();
    await this.getNewVariableNameFieldSelector(variableIndex).type(name);
    await this.page.keyboard.press('Tab');

    await this.fillVariableValueInput(
      value,
      this.getVariableTestId(variableIndex),
    );
    await this.page.keyboard.press('Tab');
  }

  async editVariableValueModificationMode(variableName: string, value: string) {
    const scope = this.getVariableTestId(variableName);
    await this.getEditVariableFieldSelector(variableName).click();
    await this.clearVariableValueInput(scope);
    await this.fillVariableValueInput(value, scope);
    await this.page.keyboard.press('Tab');
  }

  async clickEditVariableButton(variableName: string): Promise<void> {
    await this.getVariableTestId(variableName).getByLabel('Edit').click();
  }

  async clickVariableValueInput(variableName: string): Promise<void> {
    await this.variableRow(variableName)
      .getByRole('group', {name: variableName, exact: true})
      .filter({has: this.page.getByRole('textbox')})
      .click();
  }

  async fillVariableValueInput(value: string, scope?: Locator) {
    // In modification mode every variable row renders its own inline value
    // editor, so the page-wide `this.editor` matches several elements and trips
    // Playwright's strict mode. When a caller knows which variable row is being
    // edited, scope the visibility guard to that row's editor; keyboard input
    // still goes to the focused field either way.
    const editor = scope
      ? scope.getByRole('group').filter({has: this.page.getByRole('textbox')})
      : this.editor;
    await expect(editor).toBeVisible();
    await this.page.keyboard.insertText(value);
  }

  async clearVariableValueInput(scope?: Locator) {
    const editor = scope
      ? scope.getByRole('group').filter({has: this.page.getByRole('textbox')})
      : this.editor;
    await expect(editor).toBeVisible();
    // ControlOrMeta: on macOS, Control+A moves the cursor instead of selecting.
    await this.page.keyboard.press('ControlOrMeta+A');
    await this.page.keyboard.press('Backspace');
  }

  async clickSaveVariableButton(): Promise<void> {
    await this.saveVariableButton.click();
  }

  async clickAddVariableButton(): Promise<void> {
    await this.addVariableButton.click();
  }

  async fillNewVariable(name: string, value: string): Promise<void> {
    await this.newVariableNameField.fill(name);
    await this.newVariableValueField.click();
    await this.fillVariableValueInput(value);
  }

  async getProcessInstanceKey(): Promise<string> {
    const table = this.instanceHeader.locator('table');
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

  getExecutionListenerRowsByEventType(
    eventType: 'Start' | 'End' | 'Before all',
  ): Locator {
    return this.getListenerRows('execution').filter({
      has: this.page.getByRole('cell', {name: eventType, exact: true}),
    });
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
    await this.clickModifyInstanceButton();
    await this.continueButton.click();
  }

  // Below Carbon's `xlg` (1312px) breakpoint -- which Playwright's default
  // viewport sits under -- the instance header collapses its operation
  // buttons (Suspend/Cancel/Modify/Migrate) into an "Actions" menu instead of
  // rendering them directly. Open that menu when present and click the
  // corresponding item; otherwise click the direct button.
  private async clickInstanceHeaderAction(
    directButtonName: RegExp,
    collapsedMenuItemName: string,
  ): Promise<void> {
    const actionsMenuButton = this.instanceHeader.getByRole('button', {
      name: 'Actions',
    });
    const directButton = this.instanceHeader.getByRole('button', {
      name: directButtonName,
    });
    // The header renders one of these two layouts asynchronously (it depends
    // on data fetched after navigation), so wait for whichever one appears
    // rather than snapshotting with .count() -- an immediate check would
    // otherwise race the header's first render and find neither.
    await actionsMenuButton.or(directButton).first().waitFor();
    if (await actionsMenuButton.isVisible()) {
      await actionsMenuButton.click();
      await this.page
        .getByRole('menuitem', {name: collapsedMenuItemName})
        .click();
      return;
    }
    await directButton.click();
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

  /**
   * Reloads until the header shows the instance as suspended.
   *
   * Operate resolves the instance state when the detail view loads, so a
   * suspension applied over REST only shows after a reload. One reload is not
   * enough: the view can still be served the pre-suspension state for a moment
   * after the API reports SUSPENDED.
   */
  async reloadUntilSuspended(timeout = 15000): Promise<void> {
    await this.page.reload();
    await waitForAssertion({
      assertion: async () => {
        await expect(this.suspendedStateIcon).toBeVisible({timeout});
      },
      onFailure: async () => {
        await this.page.reload();
      },
    });
  }

  async suspendInstance(instanceId: string): Promise<void> {
    await this.clickInstanceHeaderAction(
      new RegExp(`Suspend Instance ${instanceId}`),
      'Suspend',
    );
  }

  async resumeInstance(instanceId: string): Promise<void> {
    await this.clickInstanceHeaderAction(
      new RegExp(`Resume Instance ${instanceId}`),
      'Resume',
    );
  }

  // The tab appears only once the incident reaches Operate's view, and the
  // table only once the tab is open — so wait for the tab, don't probe it.
  async openIncidentsTab(): Promise<void> {
    await waitForAssertion({
      assertion: async () => {
        await expect(this.incidentsTab).toBeVisible({timeout: 15_000});
        await this.incidentsTab.click();
        await expect(this.incidentsTable).toBeVisible({timeout: 15_000});
      },
      onFailure: async () => {
        await this.page.reload();
      },
    });
  }

  operationsLogEntry(operationType: string): Locator {
    return this.operationsLogTableRow
      .getByText(operationType, {exact: false})
      .first();
  }

  async instanceHeaderActions(): Promise<InstanceHeaderAction[]> {
    const actionsMenuButton = this.instanceHeader.getByRole('button', {
      name: 'Actions',
    });
    await actionsMenuButton
      .or(
        this.instanceHeader.getByRole('button', {
          name: /^Cancel Instance \d+$/,
        }),
      )
      .first()
      .waitFor();
    if (await actionsMenuButton.isVisible()) {
      await actionsMenuButton.click();
      const names = await this.page.getByRole('menuitem').allInnerTexts();
      await this.page.keyboard.press('Escape');
      return INSTANCE_HEADER_ACTIONS.filter(({menuItem}) =>
        names.some((name) => menuItem.test(name.trim())),
      ).map(({action}) => action);
    }
    const present: InstanceHeaderAction[] = [];
    for (const {action, button} of INSTANCE_HEADER_ACTIONS) {
      if (
        await this.instanceHeader
          .getByRole('button', {name: button})
          .isVisible()
      ) {
        present.push(action);
      }
    }
    return present;
  }

  async cancelInstance(instanceId: string): Promise<void> {
    await this.clickInstanceHeaderAction(
      new RegExp(`Cancel Instance ${instanceId}`),
      'Cancel',
    );
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
    return this.incidentsTable.getByRole('row').filter({
      has: this.page.getByRole('cell', {name: errorType, exact: true}),
    });
  }

  async retryIncidentByErrorType(errorType: string) {
    const incidentRow = await this.getIncidentRowByErrorType(errorType);
    await incidentRow
      .getByRole('button', {name: 'Retry Incident', exact: true})
      .click();
  }

  async clickModifyInstanceButton(): Promise<void> {
    await this.clickInstanceHeaderAction(/modify instance/i, 'Modify');
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
    const parentLocator =
      await this.getNestedParentLocatorInHistory(expandingElementName);
    // The instance history tree renders asynchronously after modifications are
    // applied, so wait for the node to appear before collecting matches —
    // otherwise .all() resolves to an empty list and nothing gets expanded.
    await expect(parentLocator.first()).toBeVisible({timeout: 30000});
    const expandingElements = await parentLocator.all();
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

  /**
   * @param itemName - The name of the history item to verify
   * @param expectedStatus - Array of icons in expected order in history, can be 'COMPLETED', 'ACTIVE', 'TERMINATED', 'INCIDENT'
   */
  async verifyHistoryItemsStatus(
    itemName: string,
    expectedStatus: string[],
  ): Promise<void> {
    const historyItems = this.instanceHistory.getByRole('treeitem', {
      name: itemName,
    });
    await expect(historyItems).toHaveCount(expectedStatus.length);
    const states = await Promise.all(
      (await historyItems.all()).map((historyItem) =>
        historyItem
          .getByRole('img', {name: / state$/})
          .first()
          .getAttribute('aria-label'),
      ),
    );
    expect(states).toEqual(
      expectedStatus.map((status) => `${status.toLowerCase()} state`),
    );
  }

  async clickOnElementInDiagram(elementId: string): Promise<void> {
    await this.clickDiagramElement(elementId);
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
    expect(actualCount).toBeGreaterThanOrEqual(expectedCount);
  }

  getOperationsLogTableRowCount(): Promise<number> {
    return this.operationsLogTableRow.count();
  }

  getOperationsLogTableProcessInstanceCellCount(): Promise<number> {
    return this.operationsLogTableProcessInstanceCell.count();
  }

  async verifyIncidents(errorTypes: string[], elementId?: string) {
    // Activate the Incidents tab BEFORE selecting the diagram element, so the
    // element selection is the last navigation. Operate's bottom-panel tab nav
    // navigates with a `location` captured by useLocation(), so clicking the
    // (already active) Incidents tab shortly after a selection change
    // re-navigates with the stale search string and reverts ?elementId to the
    // previously selected element. The incidents table then stays scoped to
    // that element and this method asserts against its rows -- which
    // verifyIncidentCount cannot catch, being a `>=` check that one stale row
    // already satisfies.
    await this.clickIncidentsTab();
    if (elementId) {
      await this.clickOnElementInDiagram(elementId);
      await expect(this.page).toHaveURL(new RegExp(`elementId=${elementId}`));
    }
    await this.verifyIncidentCount(errorTypes.length);
    for (const errorType of errorTypes) {
      const incidentRow = await this.getIncidentRowByErrorType(errorType);
      await expect(incidentRow).toBeVisible({timeout: 30000});
    }
  }
}

export {OperateProcessInstancePage};
export type {InstanceHeaderAction};
