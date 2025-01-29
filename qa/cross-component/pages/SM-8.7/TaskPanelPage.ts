import {Page, Locator, expect} from '@playwright/test';

class TaskPanelPage {
  private page: Page;
  readonly availableTasks: Locator;
  readonly filterOptions: Locator;
  readonly tasklistBanner: Locator;
  readonly collapseFilter: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.filterOptions = page
      .locator('[aria-label="Filter controls"] li')
      .filter({hasText: 'Expand to show filters'});
    this.tasklistBanner = page.getByRole('link', {
      name: 'Camunda logo Tasklist',
    });
    this.collapseFilter = page.locator(
      'button[aria-controls="task-nav-bar"][aria-expanded="true"]',
    );
  }

  async openTask(name: string): Promise<void> {
    await this.availableTasks
      .getByText(name, {exact: true})
      .nth(0)
      .click({timeout: 180000});
  }

  async filterBy(
    option: 'All open' | 'Unassigned' | 'Assigned to me' | 'Completed',
  ): Promise<void> {
    await this.filterOptions.click({timeout: 45000});
    await expect(this.page.getByRole('link', {name: option})).toBeVisible({
      timeout: 120000,
    });
    await this.page.getByRole('link', {name: option}).click({timeout: 120000});
  }

  async scrollToLastTask(name: string): Promise<void> {
    await this.page.getByText(name).last().scrollIntoViewIfNeeded();
  }

  async scrollToFirstTask(name: string): Promise<void> {
    await this.page.getByText(name).first().scrollIntoViewIfNeeded();
  }

  async clickCollapseFilter(): Promise<void> {
    await this.collapseFilter.click({timeout: 45000});
  }
}

export {TaskPanelPage};
