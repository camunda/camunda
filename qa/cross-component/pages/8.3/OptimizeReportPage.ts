import {Page, Locator, expect} from '@playwright/test';

class OptimizeReportPage {
  private page: Page;
  readonly processSelectionButton: Locator;
  readonly userTaskProcess: Locator;
  readonly versionSelection: Locator;
  readonly alwaysDisplayLatestSelection: Locator;
  readonly createReportLink: Locator;
  readonly durationButton: Locator;
  readonly countOption: Locator;
  readonly processInstanceButton: Locator;
  readonly userTaskOption: Locator;
  readonly heatMapButton: Locator;
  readonly numberButton: Locator;
  readonly tableOption: Locator;
  readonly reportName: Locator;
  readonly saveButton: Locator;
  readonly oneUserTaskInstance: Locator;
  readonly twoUserTaskInstance: Locator;
  readonly cancelButton: Locator;
  readonly createNewButton: Locator;
  readonly reportOption: Locator;
  readonly processInstanceCount: Locator;
  readonly blankReportButton: Locator;
  readonly selectDropdown: Locator;

  constructor(page: Page) {
    this.page = page;
    this.processSelectionButton = page.getByPlaceholder('Select...');
    this.userTaskProcess = page.getByText('User_Task_Process', {exact: true});
    this.versionSelection = page.getByLabel('Version');
    this.alwaysDisplayLatestSelection = page.getByText('Always display latest');
    this.createReportLink = page.getByRole('link', {name: 'Create Report'});
    this.durationButton = page.getByRole('button', {name: 'Duration'});
    this.countOption = page.getByText('Count').first();
    this.processInstanceButton = page.getByRole('button', {
      name: 'Process Instance',
      exact: true,
    });
    this.userTaskOption = page.getByText('User Task', {exact: true});
    this.heatMapButton = page.getByRole('button', {name: 'Heatmap'});
    this.numberButton = page.getByRole('button', {name: 'Number'});
    this.tableOption = page.getByText('Table');
    this.reportName = page.getByPlaceholder('Report name');
    this.saveButton = page.getByRole('button', {name: 'Save'});
    this.oneUserTaskInstance = page.getByRole('cell', {name: '1', exact: true});
    this.twoUserTaskInstance = page.getByRole('cell', {name: '2', exact: true});
    this.cancelButton = page.getByRole('button', {name: 'Cancel'});
    this.createNewButton = page.getByRole('button', {
      name: 'Create New',
      exact: true,
    });
    this.reportOption = page.getByText('Report', {exact: true});
    this.processInstanceCount = page.locator('.progressLabel');
    this.blankReportButton = page.getByRole('button', {name: 'Blank report'});
    this.selectDropdown = page
      .locator('li')
      .filter({
        hasText:
          'ViewSelect...Raw DataProcess InstanceIncidentFlow NodeUser TaskVariable',
      })
      .getByRole('button');
  }

  async clickProcessSelectionButton(): Promise<void> {
    await this.processSelectionButton.click();
  }

  async clickUserTaskProcess(processName: string): Promise<void> {
    await this.page
      .getByText(processName, {exact: true})
      .first()
      .click({timeout: 60000});
  }

  async clickVersionSelection(): Promise<void> {
    await this.versionSelection.click();
  }

  async clickAlwaysDisplayLatestSelection(): Promise<void> {
    await this.alwaysDisplayLatestSelection.click();
  }

  async clickCreateReportLink(): Promise<void> {
    await this.createReportLink.click({timeout: 60000});
  }

  async clickDurationButton(): Promise<void> {
    await this.durationButton.click();
  }

  async clickCountOption(): Promise<void> {
    await this.countOption.click();
  }

  async clickProcessInstanceButton(): Promise<void> {
    await this.processInstanceButton.click();
  }

  async clickUserTaskOption(): Promise<void> {
    await this.userTaskOption.click();
  }

  async clickHeatMapButton(): Promise<void> {
    if (await this.heatMapButton.isVisible()) {
      await this.heatMapButton.click();
    }
  }

  async clickNumberButton(): Promise<void> {
    if (await this.numberButton.isVisible()) {
      await this.numberButton.click();
    }
  }

  async clickTableOption(): Promise<void> {
    await this.tableOption.click();
  }

  async clickReportName(): Promise<void> {
    await this.reportName.click();
  }

  async clearReportName(): Promise<void> {
    await this.reportName.clear({force: true});
  }

  async fillReportName(name: string): Promise<void> {
    await this.reportName.fill(name, {force: true});
  }

  async clickSaveButton(): Promise<void> {
    await this.saveButton.click();
  }

  async waitUntilLocatorIsVisible(locator: Locator, tab: Page): Promise<void> {
    let elapsedTime = 0;
    const maxWaitTimeSeconds: number = 120000;

    while (elapsedTime < maxWaitTimeSeconds) {
      const element = await locator;

      if (await element.isVisible()) {
        return;
      }

      await tab.reload();
      await tab.waitForTimeout(10 * 1000);
      // Update the elapsed time
      elapsedTime += 10000;
    }
  }

  async waitUntilProcessIsVisible(locator: Locator, tab: Page): Promise<void> {
    let elapsedTime = 0;
    const maxWaitTimeSeconds: number = 240000;

    while (elapsedTime < maxWaitTimeSeconds) {
      const element = await locator;

      if (await element.isVisible()) {
        return;
      }

      await this.cancelButton.click();
      await tab.reload();
      await tab.waitForTimeout(10 * 1000);
      // Update the elapsed time
      elapsedTime += 10000;

      await this.createNewButton.click();
      await this.reportOption.click();
      await this.processSelectionButton.click();
    }
  }

  async clickBlankReportButton(): Promise<void> {
    await this.blankReportButton.click();
  }

  async clickSelectDropdown(): Promise<void> {
    await this.selectDropdown.click();
  }

  async userTaskAssertion(userTaskLocator: Locator): Promise<void> {
    try {
      await expect(userTaskLocator).toBeVisible({timeout: 180000});
    } catch {
      this.page.reload();
      await expect(userTaskLocator).toBeVisible({timeout: 180000});
    }
  }
}
export {OptimizeReportPage};
