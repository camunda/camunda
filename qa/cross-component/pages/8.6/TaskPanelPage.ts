import {Page, Locator, expect} from '@playwright/test';
import {sleep} from '../../utils/sleep';

class TaskPanelPage {
  private page: Page;
  readonly availableTasks: Locator;
  readonly filterPanel: Locator;
  readonly taskListPageBanner: Locator;
  readonly processesPageTab: Locator;
  readonly tasksTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.availableTasks = page.getByTitle('Available tasks');
    this.filterPanel = page.locator('[aria-controls="task-nav-bar"]');
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
      .click({timeout: 180000});
  }

  async taskCount(name: string) {
    await sleep(20000);
    return this.availableTasks.getByText(name, {exact: true}).count();
  }

  async clickTasksTab(): Promise<void> {
    await this.tasksTab.click();
  }

  async filterBy(
    option: 'All open' | 'Unassigned' | 'Assigned to me' | 'Completed',
  ): Promise<void> {
    await this.filterPanel.click({timeout: 120000});
    await expect(
      this.page.getByRole('link', {name: option}).getByText(option),
    ).toBeVisible({timeout: 120000});
    await this.page
      .getByRole('link', {name: option})
      .getByText(option)
      .click({timeout: 120000});
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
