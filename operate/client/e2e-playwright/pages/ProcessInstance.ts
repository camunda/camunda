/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page, Locator} from '@playwright/test';
import {Diagram} from './components/Diagram';

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
  readonly executionCountToggleOn: Locator;
  readonly executionCountToggleOff: Locator;
  readonly listenersTabButton: Locator;
  readonly metadataModal: Locator;
  readonly modifyInstanceButton: Locator;
  readonly listenerTypeFilter: Locator;
  readonly resetZoomButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.instanceHeader = page.getByTestId('instance-header');
    this.instanceHistory = page.getByTestId('instance-history');
    this.variablesList = page.getByTestId('variables-list');
    this.incidentsTable = page.getByTestId('data-list');
    this.incidentsBanner = page.getByTestId('incidents-banner');
    this.diagram = new Diagram(page);
    this.variablePanelEmptyText = page.getByText(
      /to view the variables, select a single flow node instance in the instance history./i,
    );
    this.addVariableButton = page.getByRole('button', {name: 'Add variable'});
    this.saveVariableButton = page.getByRole('button', {name: 'Save variable'});
    this.newVariableNameField = page.getByRole('textbox', {name: 'Name'});
    this.newVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.editVariableValueField = page.getByRole('textbox', {name: 'Value'});
    this.variablesTableSpinner = page.getByTestId('variables-spinner');
    this.variableSpinner = page.getByTestId('variable-operation-spinner');
    this.operationSpinner = page.getByTestId('operation-spinner');
    this.executionCountToggleOn = this.instanceHistory.getByLabel(
      /^show execution count$/i,
    );
    this.executionCountToggleOff = this.instanceHistory.getByLabel(
      /^hide execution count$/i,
    );
    this.listenersTabButton = page.getByTestId('listeners-tab-button');
    this.metadataModal = this.page.getByRole('dialog', {name: /metadata/i});
    this.modifyInstanceButton = page.getByTestId('enter-modification-mode');
    this.listenerTypeFilter = page.getByTestId('listener-type-filter');
    this.resetZoomButton = page.getByRole('button', {
      name: 'Reset diagram zoom',
    });
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
    id,
    options,
  }: {
    id: string;
    options?: Parameters<Page['goto']>[1];
  }) {
    await this.page.goto(`/operate/processes/${id}`, options);
  }

  async getNthTreeNodeTestId(n: number) {
    return this.page
      .getByTestId(/^tree-node-/)
      .nth(n)
      .getAttribute('data-testid');
  }
}
