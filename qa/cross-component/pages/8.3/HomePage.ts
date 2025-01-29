import {Page, Locator} from '@playwright/test';

class HomePage {
  private page: Page;
  readonly dashboardTab: Locator;
  readonly clusterTab: Locator;
  readonly organizationTab: Locator;
  readonly organizationViewTitle: Locator;
  readonly welcomeMessage: Locator;
  readonly informationDialog: Locator;
  readonly camundaAppsButton: Locator;
  readonly camundaComponentsButton: Locator;
  readonly appSwitcher: Locator;
  readonly openOrganizationButton: Locator;
  readonly consoleBanner: Locator;
  readonly gettingStartedHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.dashboardTab = page.getByRole('link', {name: 'Dashboard'});
    this.clusterTab = page
      .getByRole('banner')
      .getByRole('link', {name: 'Clusters'});
    this.organizationTab = page.getByRole('link', {name: 'Organization'});
    this.organizationViewTitle = page.getByText('Organization Management');
    this.welcomeMessage = page.getByText('Welcome to Camunda!').first();
    this.informationDialog = page.getByRole('button', {
      name: 'Close this dialog',
    });
    this.camundaComponentsButton = page.getByLabel('Camunda components');
    this.camundaAppsButton = page.getByLabel('Camunda apps');
    this.appSwitcher = page.getByLabel('App Switcher');
    this.openOrganizationButton = page.getByLabel('Open Organizations');
    this.consoleBanner = page.getByRole('link', {
      name: 'Camunda logo Console',
    });
    this.gettingStartedHeading = page.getByText('start by creating a project');
  }

  async clickClusters(): Promise<void> {
    await this.clusterTab.click({timeout: 90000});
  }

  async clickDashboard(): Promise<void> {
    await this.dashboardTab.click({timeout: 90000});
  }

  async clickOrganization(): Promise<void> {
    await this.organizationTab.click({timeout: 120000});
  }

  async closeInformationDialog(): Promise<void> {
    const isLocalTest = process.env.LOCAL_TEST;
    if (isLocalTest) {
      await this.informationDialog.click();
    }
  }

  async clickCamundaComponents(): Promise<void> {
    await this.camundaComponentsButton.click({timeout: 90000});
  }

  async clickCamundaApps(): Promise<void> {
    try {
      await this.camundaAppsButton.click({timeout: 60000});
    } catch (error) {
      // If clicking camundaAppsButton fails, try camundaComponentsButton
      try {
        await this.camundaComponentsButton.click({timeout: 60000});
      } catch (error) {
        // If clicking camundaComponentsButton also fails, click appSwitcher
        try {
          await this.appSwitcher.click({timeout: 60000});
        } catch (error) {
          // If all attempts fail, throw an error
          throw new Error('Failed to click any button');
        }
      }
    }
  }

  async clickOpenOrganizationButton(): Promise<void> {
    await this.openOrganizationButton.click({timeout: 60000});
  }

  organizationUuid(): string {
    const uuidPattern = /\/org\/([a-f0-9-]+)/;
    const originalMatch = this.page.url().match(uuidPattern);
    if (!originalMatch || originalMatch.length != 2) {
      throw new Error('Organization UUID not found in the URL');
    }
    return originalMatch[1];
  }
}

export {HomePage};
