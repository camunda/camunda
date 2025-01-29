import {Page, Locator} from '@playwright/test';

class AppsPage {
  private page: Page;
  readonly modelerLink: Locator;
  readonly tasklistLink: Locator;
  readonly operateLink: Locator;
  readonly appSwitcherButton: Locator;
  readonly optimizeLink: Locator;
  readonly optimizeFilter: Locator;
  readonly consoleLink: Locator;
  readonly camundaComponentsButton: Locator;
  readonly camundaAppsButton: Locator;
  readonly operateFilter: Locator;
  readonly tasklistFilter: Locator;

  constructor(page: Page) {
    this.page = page;
    this.modelerLink = page.getByRole('link', {name: 'Modeler', exact: true});
    this.appSwitcherButton = page.getByLabel('App Switcher');
    this.tasklistLink = page.getByRole('link', {name: 'Tasklist'});
    this.operateLink = page.getByRole('link', {name: 'Operate', exact: true});
    this.optimizeLink = page.getByRole('link', {name: 'Optimize', exact: true});
    this.optimizeFilter = page
      .locator('a')
      .filter({hasText: 'Optimize'})
      .first();
    this.consoleLink = page.getByRole('link', {name: 'Console', exact: true});
    this.camundaComponentsButton = page.getByLabel('Camunda components');
    this.camundaAppsButton = page.getByLabel('Camunda apps');
    this.operateFilter = page.locator('a').filter({hasText: 'Operate'}).first();
    this.tasklistFilter = page
      .locator('a')
      .filter({hasText: 'Tasklist'})
      .first();
  }

  async clickModelerLink(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.modelerLink.click({timeout: 60000});
        } else {
          await this.camundaComponentsButton.click({timeout: 60000});
          await this.modelerLink.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the modeler link after ${maxRetries} attempts.`,
    );
  }

  async clickAppSwitcherButton(): Promise<void> {
    try {
      await this.appSwitcherButton.click({timeout: 60000});
    } catch (error) {
      await this.camundaAppsButton.click({timeout: 60000});
    }
  }

  async clickTasklistLink(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.tasklistLink.click({timeout: 60000});
        } else {
          await this.clickCamundaApps();
          await this.tasklistLink.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the tasklist link after ${maxRetries} attempts.`,
    );
  }

  async clickOperateLink(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.operateLink.click({timeout: 60000});
        } else {
          await this.camundaComponentsButton.click({timeout: 60000});
          await this.operateLink.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the operate link after ${maxRetries} attempts.`,
    );
  }

  async clickOptimizeLink(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.optimizeLink.click({timeout: 60000});
        } else {
          await this.camundaComponentsButton.click({timeout: 60000});
          await this.optimizeLink.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the optimize link after ${maxRetries} attempts.`,
    );
  }

  async clickOptimizeFilter(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.optimizeFilter.click({timeout: 60000});
        } else {
          await this.camundaComponentsButton.click({timeout: 60000});
          await this.optimizeFilter.click({timeout: 30000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the optimize link after ${maxRetries} attempts.`,
    );
  }

  async clickCamundaAppsLink(): Promise<void> {
    try {
      await this.camundaComponentsButton.click({timeout: 60000});
    } catch (error) {
      await this.camundaAppsButton.click({timeout: 60000});
    }
  }

  async clickCamundaApps(): Promise<void> {
    try {
      await this.camundaAppsButton.click({timeout: 60000});
    } catch (error) {
      // If clicking camundaAppsButton fails, try camundaComponentsButton
      try {
        await this.camundaComponentsButton.click({timeout: 90000});
      } catch (error) {
        // If clicking camundaComponentsButton also fails, click appSwitcher
        try {
          await this.appSwitcherButton.click({timeout: 90000});
        } catch (error) {
          // If all attempts fail, throw an error
          throw new Error('Failed to click any button');
        }
      }
    }
  }

  async clickOperateFilter(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.operateFilter.click();
        } else {
          await this.camundaComponentsButton.click({timeout: 60000});
          await this.operateFilter.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the Tasklist link after ${maxRetries} attempts.`,
    );
  }

  async clickTasklistFilter(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.tasklistFilter.click({timeout: 60000});
        } else {
          await this.camundaComponentsButton.click({timeout: 60000});
          await this.tasklistFilter.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the Tasklist link after ${maxRetries} attempts.`,
    );
  }

  async clickConsoleLink(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.consoleLink.click({timeout: 60000});
        } else {
          await this.clickCamundaApps();
          await this.consoleLink.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the console link after ${maxRetries} attempts.`,
    );
  }
}

export {AppsPage};
