import {Page, Locator, expect} from '@playwright/test';

class TaskPanelPage {
  private page: Page;
  readonly availableTasks: Locator;
  readonly filterOptions: Locator;
  readonly tasklistBanner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.filterOptions = page.getByRole('combobox', {name: 'Filter options'});
    this.tasklistBanner = page.getByRole('link', {
      name: 'Camunda logo Tasklist',
    });
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
    await expect(
      this.page.getByRole('option', {name: option}).getByText(option),
    ).toBeVisible({timeout: 120000});
    await this.page
      .getByRole('option', {name: option})
      .getByText(option)
      .click({timeout: 120000});
  }

  async scrollToLastTask(name: string): Promise<void> {
    await this.page.getByText(name).last().scrollIntoViewIfNeeded();
  }

  async scrollToFirstTask(name: string): Promise<void> {
    await this.page.getByText(name).first().scrollIntoViewIfNeeded();
  }
}

export {TaskPanelPage};
