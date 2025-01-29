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

  constructor(page: Page) {
    this.page = page;
    this.dashboardTab = page.getByRole('link', {name: 'Dashboard'});
    this.clusterTab = page.getByRole('link', {name: 'Clusters'});
    this.organizationTab = page.getByRole('link', {name: 'Organization'});
    this.organizationViewTitle = page.getByText('Organization Management');
    this.welcomeMessage = page.getByText('Welcome, demo').first();
    this.informationDialog = page.getByRole('button', {
      name: 'Close this dialog',
    });
    this.camundaComponentsButton = page.getByLabel('Camunda components');
    this.camundaAppsButton = page.getByLabel('Camunda apps');
  }

  async clickClusters(): Promise<void> {
    await this.clusterTab.click({timeout: 90000});
  }

  async clickDashboard(): Promise<void> {
    await this.dashboardTab.click({timeout: 90000});
  }

  async clickOrganization(): Promise<void> {
    await this.organizationTab.click({timeout: 90000});
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
    await this.camundaAppsButton.click({timeout: 90000});
  }
}
export {HomePage};
