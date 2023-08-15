/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';
import {Paths} from 'modules/Routes';

export class ProcessInstance {
  private page: Page;
  readonly instanceHeader: Locator;
  readonly instanceHistory: Locator;
  readonly diagram: Locator;
  readonly popover: Locator;
  readonly variablePanelEmptyText: Locator;

  constructor(page: Page) {
    this.page = page;
    this.instanceHeader = page.getByTestId('instance-header');
    this.instanceHistory = page.getByTestId('instance-history');
    this.diagram = page.getByTestId('diagram');
    this.popover = page.getByTestId('popover');
    this.variablePanelEmptyText = page.getByText(
      /to view the variables, select a single flow node instance in the instance history./i,
    );
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

  async undoModification() {
    await this.page
      .getByRole('button', {
        name: /undo/i,
      })
      .click();
  }

  async navigateToProcessInstance(id: string) {
    await this.page.goto(Paths.processInstance(id));
  }

  async selectFlowNode(flowNodeName: string) {
    await this.diagram.getByText(flowNodeName).click();
  }
}
