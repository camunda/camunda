import {Page, Locator} from '@playwright/test';

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
    await this.processActiveCheckbox.click();
  }

  async clickProcessCompletedCheckbox(): Promise<void> {
    await this.processCompletedCheckbox.click({timeout: 120000});
  }

  async clickProcessIncidentsCheckbox(): Promise<void> {
    await this.processIncidentsCheckbox.click({timeout: 90000});
  }

  async clickRunningProcessInstancesCheckbox(): Promise<void> {
    await this.processRunningInstancesCheckbox.click({timeout: 90000});
  }

  async clickFinishedProcessInstancesCheckbox(): Promise<void> {
    await this.processFinishedInstancesCheckbox.click({timeout: 90000});
  }

  async clickProcessInstanceLink(
    processName: string,
    fallbackSelector?: string,
  ): Promise<void> {
    let retryCount = 0;
    const maxRetries = 10;

    while (retryCount < maxRetries) {
      try {
        await this.page
          .locator(`td:right-of(:text("${processName}"))`)
          .first()
          .click({timeout: 90000});
        return;
      } catch (error) {
        retryCount++;
        console.log(`Attempt ${retryCount} failed. Retrying...`);

        if (retryCount >= maxRetries && fallbackSelector) {
          try {
            console.log(`Retrying with fallback selector: ${fallbackSelector}`);
            await this.page
              .locator(fallbackSelector)
              .first()
              .click({timeout: 90000});
            return;
          } catch (fallbackError) {
            console.error(
              `Failed to click using the fallback selector: ${fallbackSelector}`,
            );
            throw new Error(
              `Failed to click on process instance link and fallback after ${maxRetries} attempts.`,
            );
          }
        } else {
          await this.page.reload();
          await new Promise((resolve) => setTimeout(resolve, 15000));
        }
      }
    }

    throw new Error(
      `Failed to click on process instance link after ${maxRetries} attempts.`,
    );
  }
}

export {OperateProcessesPage};
