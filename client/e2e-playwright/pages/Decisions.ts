/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';
import {Paths} from 'modules/Routes';

export class Decisions {
  private page: Page;
  readonly decisionNameFilter: Locator;
  readonly decisionVersionFilter: Locator;
  readonly decisionViewer: Locator;

  constructor(page: Page) {
    this.page = page;

    this.decisionNameFilter = page.getByRole('combobox', {
      name: 'Name',
    });
    this.decisionVersionFilter = page.getByRole('combobox', {
      name: 'Version',
    });

    this.decisionViewer = page.getByTestId('decision-viewer');
  }

  async selectDecision(option: string) {
    await this.decisionNameFilter.click();
    await this.page.getByTestId('expanded-panel').getByText(option).click();
  }

  async selectVersion(option: string) {
    await this.decisionVersionFilter.click();
    await this.page.getByTestId('expanded-panel').getByText(option).click();
  }

  async navigateToDecisions(searchParams: string = '') {
    await this.page.goto(`${Paths.decisions()}${searchParams}`);
  }

  async clearComboBox() {
    await this.page
      .getByRole('button', {
        name: 'Clear selected item',
      })
      .click();
  }
}
