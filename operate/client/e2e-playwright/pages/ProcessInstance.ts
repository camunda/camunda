/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';
import {Diagram} from './components/Diagram';
import {JSONEditor} from './components/JSONEditor';

export class ProcessInstance {
  private page: Page;
  readonly instanceHeader: Locator;
  readonly instanceHistory: Locator;
  readonly variablesList: Locator;
  readonly incidentsTable: Locator;
  readonly incidentsBanner: Locator;
  readonly diagram: InstanceType<typeof Diagram>;
  readonly variablePanelEmptyText: Locator;
  readonly addVariableButton: Locator;
  readonly saveVariableButton: Locator;
  readonly newVariableNameField: Locator;
  readonly newVariableValueField: Locator;
  readonly editVariableValueField: Locator;
  readonly variablesTableSpinner: Locator;
  readonly variableSpinner: Locator;
  readonly operationSpinner: Locator;
  readonly executionCountToggle: Locator;
  readonly endDateToggle: Locator;
  readonly listenersTabButton: Locator;
  readonly operationsLogTabButton: Locator;
  readonly operationsLogTable: Locator;
  readonly operationsLogTableSpinner: Locator;
  readonly metadataModal: Locator;
  readonly modifyInstanceButton: Locator;
  readonly listenerTypeFilter: Locator;
  readonly resetZoomButton: Locator;
  readonly variablesEditor: InstanceType<typeof JSONEditor>;

  constructor(page: Page) {
    this.page = page;
    this.instanceHeader = page.getByTestId('instance-header');
    this.instanceHistory = page.getByTestId('instance-history');
    this.variablesList = page.getByTestId('variables-list');
    this.incidentsTable = page.getByTestId('data-list');
    this.incidentsBanner = page.getByTestId('incidents-banner');
    this.diagram = new Diagram(page);
    this.variablePanelEmptyText = page.getByText(
      /to view the variables, select a single element instance in the instance history./i,
    );
    this.addVariableButton = page.getByRole('button', {name: 'Add variable'});
    this.saveVariableButton = page.getByRole('button', {name: 'Save variable'});
    this.newVariableNameField = page.getByRole('textbox', {name: 'Name'});
    this.newVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.editVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.variablesTableSpinner = page.getByTestId('variables-spinner');
    this.variableSpinner = page.getByTestId('variable-operation-spinner');
    this.operationSpinner = page.getByTestId('operation-spinner');
    this.executionCountToggle =
      this.instanceHistory.getByLabel('Execution count');
    this.endDateToggle = this.instanceHistory.getByLabel('End date');
    this.listenersTabButton = page.getByTestId('listeners-tab-button');
    this.operationsLogTabButton = page.getByRole('link', {
      name: 'Operations Log',
    });
    this.operationsLogTable = page
      .getByTestId('data-table-container')
      .getByRole('table');
    this.operationsLogTableSpinner = page.getByTestId('data-table-loader');
    this.metadataModal = this.page.getByRole('dialog', {name: /metadata/i});
    this.modifyInstanceButton = page.getByTestId('enter-modification-mode');
    this.listenerTypeFilter = page.getByTestId('listener-type-filter');
    this.resetZoomButton = page.getByRole('button', {
      name: 'Reset diagram zoom',
    });
    this.variablesEditor = new JSONEditor(page);
  }

  getEditVariableFieldSelector(variableName: string) {
    return this.page
      .getByTestId(`variable-${variableName}`)
      .getByRole('textbox', {
        name: /value/i,
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
        name: /undo/i,
      })
      .click();
  }

  async gotoProcessInstancePage({
    key,
    options,
  }: {
    key: string;
    options?: Parameters<Page['goto']>[1];
  }) {
    await this.page.goto(`/operate/processes/${key}`, options);
  }

  async gotoProcessInstanceOperationsLogPage({
    key,
    options,
  }: {
    key: string;
    options?: Parameters<Page['goto']>[1];
  }) {
    await this.gotoProcessInstancePage({key, options});
    await this.operationsLogTabButton.click();
  }

  async getNthTreeNodeTestId(n: number) {
    return this.page
      .getByTestId(/^tree-node-/)
      .nth(n)
      .getAttribute('data-testid');
  }
}
