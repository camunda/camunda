import {Page, Locator, expect} from '@playwright/test';

class OperateProcessesPage {
  private page: Page;
  readonly processResultCount: Locator;
  readonly processActiveCheckbox: Locator;
  readonly processCompletedCheckbox: Locator;
  readonly processRunningInstancesCheckbox: Locator;
  readonly processIncidentsCheckbox: Locator;
  readonly processPageHeading: Locator;
  readonly noMatchingInstancesMessage: Locator;
  readonly processFinishedInstancesCheckbox: Locator;

  constructor(page: Page) {
    this.page = page;
    this.processResultCount = page.getByTestId('result-count');
    this.processActiveCheckbox = page
      .locator('label')
      .filter({hasText: 'Active'});
    this.processCompletedCheckbox = page
      .locator('label')
      .filter({hasText: 'Completed'});
    this.processRunningInstancesCheckbox = page
      .locator('label')
      .filter({hasText: 'Running Instances'});
    this.processIncidentsCheckbox = page
      .locator('label')
      .filter({hasText: 'Incidents'});
    this.processPageHeading = page
      .getByTestId('expanded-panel')
      .getByRole('heading', {name: 'Process'});
    this.noMatchingInstancesMessage = page.getByText(
      'There are no Instances matching this filter set',
    );
    this.processFinishedInstancesCheckbox = page
      .getByTestId('filter-finished-instances')
      .getByRole('checkbox');
  }

  async clickProcessActiveCheckbox(): Promise<void> {
    await this.processActiveCheckbox.click({timeout: 30000});
  }

  async assertProcessNameNotVisible(processName: string): Promise<void> {
    await expect(
      this.page.getByRole('row', {name: processName}),
    ).not.toBeVisible({timeout: 60000});
  }

  async clickProcessCompletedCheckbox(): Promise<void> {
    await this.processCompletedCheckbox.click({timeout: 30000});
  }

  async clickProcessIncidentsCheckbox(): Promise<void> {
    await this.processIncidentsCheckbox.click({timeout: 30000});
  }

  async clickRunningProcessInstancesCheckbox(): Promise<void> {
    await this.processRunningInstancesCheckbox.click({timeout: 30000});
  }

  async clickFinishedProcessInstancesCheckbox(): Promise<void> {
    await this.processFinishedInstancesCheckbox.click({timeout: 30000});
  }

  async clickProcessInstanceLink(processName: string): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.page
            .locator('td:right-of(:text("' + processName + '"))')
            .first()
            .click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
        await this.page.reload();
      }
    }

    throw new Error(`Failed to click the link after ${maxRetries} attempts.`);
  }
}

export {OperateProcessesPage};
