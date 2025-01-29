import {Page, Locator, expect} from '@playwright/test';
import {sleep} from '../../utils/sleep';

class TaskPanelPage {
  private page: Page;
  readonly availableTasks: Locator;
  readonly filterOptions: Locator;
  readonly filterComboBox: Locator;
  readonly taskListPageBanner: Locator;
  readonly processesPageTab: Locator;
  readonly tasksTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.filterOptions = page.getByText('Filter optionsAll openOpen menu');
    this.filterComboBox = page.getByRole('combobox', {name: 'Filter options'});
    this.taskListPageBanner = page.getByRole('link', {
      name: 'Camunda logo Tasklist',
    });
    this.processesPageTab = page.getByRole('link', {name: 'Processes'});
    this.tasksTab = page.getByRole('link', {name: 'Tasks'});
  }

  async openTask(name: string): Promise<void> {
    await this.availableTasks
      .getByText(name, {exact: true})
      .nth(0)
      .click({timeout: 45000});
  }

  async filterBy(
    option: 'All open' | 'Unassigned' | 'Assigned to me' | 'Completed',
  ): Promise<void> {
    try {
      await this.filterOptions.click({timeout: 60000});
    } catch (error) {
      await this.filterComboBox.click({timeout: 60000});
    }
    await expect(
      this.page.getByRole('option', {name: option}).getByText(option),
    ).toBeVisible({timeout: 120000});
    await this.page
      .getByRole('option', {name: option})
      .getByText(option)
      .click({timeout: 120000});
  }

  async taskCount(name: string) {
    await sleep(20000);
    return this.availableTasks.getByText(name, {exact: true}).count();
  }

  async clickTasksTab(): Promise<void> {
    await this.tasksTab.click();
  }

  async scrollToLastTask(name: string): Promise<void> {
    await this.page.getByText(name).last().scrollIntoViewIfNeeded();
  }

  async scrollToFirstTask(name: string): Promise<void> {
    await this.page.getByText(name).first().scrollIntoViewIfNeeded();
  }

  async clickProcessesTab(): Promise<void> {
    await this.processesPageTab.click();
  }
}

export {TaskPanelPage};
