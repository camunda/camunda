import {Page, Locator, expect} from '@playwright/test';

class ClusterDetailsPage {
  private page: Page;
  readonly apiTab: Locator;
  readonly createFirstClientButton: Locator;
  readonly clientNameTextbox: Locator;
  readonly tasklistCheckbox: Locator;
  readonly secretsCheckbox: Locator;
  readonly optimizeCheckbox: Locator;
  readonly operateCheckbox: Locator;
  readonly createButton: Locator;
  readonly closeModalButton: Locator;
  readonly createClientButton: Locator;
  readonly settingsTab: Locator;
  readonly rbaEnableButton: Locator;
  readonly rbaDisableButton: Locator;
  readonly dialog: Locator;
  readonly deleteAPIClientButton: Locator;
  readonly deleteAPIClientSubButton: Locator;
  readonly createClientCredentialsDialog: Locator;
  readonly clientCredentialsDialog: Locator;
  readonly testClusterLink: Locator;
  readonly clustersLink: Locator;
  readonly userTaskRestriction: Locator;

  constructor(page: Page) {
    this.page = page;
    this.apiTab = page.getByRole('tab', {name: 'API'});
    this.createFirstClientButton = page.getByRole('button', {
      name: 'Create your first Client',
    });
    this.clientNameTextbox = page.getByRole('textbox', {name: 'Client Name'});
    this.tasklistCheckbox = page.locator('label').filter({hasText: 'Tasklist'});
    this.optimizeCheckbox = page.locator('label').filter({hasText: 'Optimize'});
    this.operateCheckbox = page.locator('label').filter({hasText: 'Operate'});
    this.secretsCheckbox = page.locator('label').filter({hasText: /^Secrets$/});
    this.createButton = page.getByRole('button', {name: 'Create', exact: true});
    this.closeModalButton = page
      .getByRole('dialog', {name: 'Client credentials', exact: true})
      .getByLabel('Close');
    this.createClientButton = page.getByRole('button', {
      name: 'Create new Client',
    });
    this.settingsTab = page.getByRole('tab', {name: 'Settings'});
    this.rbaEnableButton = page.getByRole('button', {name: 'Enable'});
    this.rbaDisableButton = page.getByRole('button', {name: 'Disable'});
    this.dialog = page.getByRole('dialog');
    this.deleteAPIClientButton = page.getByRole('button', {name: 'Delete'});
    this.deleteAPIClientSubButton = page.getByRole('button', {
      name: 'danger Delete',
    });
    this.createClientCredentialsDialog = page.getByRole('dialog', {
      name: 'Create new client credentials',
    });
    this.clientCredentialsDialog = page.getByRole('dialog', {
      name: 'Client credentials',
      exact: true,
    });
    this.testClusterLink = page.getByRole('link', {name: 'Test Cluster'});
    this.clustersLink = page
      .getByRole('banner')
      .getByRole('link', {name: 'Clusters'});
    this.userTaskRestriction = page.locator(
      'button[aria-label="Switch Enforce user task restrictions"]',
    );
  }

  async clickAPITab(): Promise<void> {
    await this.apiTab.click({timeout: 60000});
  }

  async clickCreateFirstClientButton(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.createFirstClientButton.click({timeout: 60000});
        } else {
          await this.clustersLink.click({timeout: 60000});
          await this.testClusterLink.click();
          await this.clickAPITab();
          await this.createFirstClientButton.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async clickClientNameTextbox(): Promise<void> {
    await this.clientNameTextbox.click({timeout: 60000});
  }

  async fillClientNameTextbox(name: string): Promise<void> {
    await this.clientNameTextbox.fill(name, {timeout: 60000});
  }

  async clickTasklistCheckbox(): Promise<void> {
    await this.tasklistCheckbox.click({timeout: 60000});
  }

  async checkSecretsCheckbox(): Promise<void> {
    if (!(await this.secretsCheckbox.isChecked())) {
      await this.secretsCheckbox.click();
    }
  }

  async clickOperateCheckbox(): Promise<void> {
    await this.operateCheckbox.click({timeout: 60000});
  }

  async clickOptimizeCheckbox(): Promise<void> {
    await this.optimizeCheckbox.click({timeout: 60000});
  }

  async clickCreateButton(): Promise<void> {
    await this.createButton.click({timeout: 60000});
  }

  async clickCloseModalButton(): Promise<void> {
    await this.closeModalButton.click({timeout: 60000});
  }

  async clickSettingsTab(): Promise<void> {
    await this.settingsTab.click({timeout: 60000});
  }

  async enableRBA(): Promise<void> {
    // Locate all elements with class .cds--toggle__text
    const toggleTextElements = await this.page.$$('.cds--toggle__text');

    // Check if toggleTextElements has at least 2 elements
    if (toggleTextElements.length >= 2) {
      // Get the text content of the second toggle text element
      const toggleText = await toggleTextElements[1].textContent();

      // Check if the text content is 'Disabled'
      if (toggleText === 'Disabled') {
        // Locate all elements with class .cds--toggle__switch
        const toggleSwitchElements = await this.page.$$('.cds--toggle__switch');

        // Check if toggleSwitchElements has at least 2 elements
        if (toggleSwitchElements.length >= 2) {
          // Click the second toggle switch element
          await toggleSwitchElements[1].click();
          console.log('Second toggle switch clicked.');
          await expect(this.dialog).toBeVisible({timeout: 60000});
          await this.rbaEnableButton.click();
        } else {
          console.log('Second toggle switch not found or less than 2.');
        }
      } else {
        console.log("Second toggle text does not contain 'Disabled'.");
      }
    } else {
      console.log('Toggle text elements not found or less than 2.');
    }
  }

  async disableRBA(): Promise<void> {
    // Locate all elements with class .cds--toggle__text
    const toggleTextElements = await this.page.$$('.cds--toggle__text');

    // Check if toggleTextElements has at least 2 elements
    if (toggleTextElements.length >= 2) {
      // Get the text content of the second toggle text element
      const toggleText = await toggleTextElements[1].textContent();

      // Check if the text content is 'Enabled'
      if (toggleText === 'Enabled') {
        // Locate all elements with class .cds--toggle__switch
        const toggleSwitchElements = await this.page.$$('.cds--toggle__switch');

        // Check if toggleSwitchElements has at least 2 elements
        if (toggleSwitchElements.length >= 2) {
          // Click the second toggle switch element
          await toggleSwitchElements[1].click();
          console.log('Second toggle switch clicked.');
          await expect(this.dialog).toBeVisible({timeout: 60000});
          await this.rbaDisableButton.click();
        } else {
          console.log('Second toggle switch not found or less than 2.');
        }
      } else {
        console.log("Second toggle text does not contain 'Enabled'.");
      }
    } else {
      console.log('Toggle text elements not found or less than 2.');
    }
  }

  async deleteAPIClientsIfExist(): Promise<void> {
    try {
      await expect(this.deleteAPIClientButton.first()).toBeVisible({
        timeout: 10000,
      });
    } catch (error) {
      return; //No API clients found in the list
    }

    try {
      let deletes = await this.deleteAPIClientButton.all();
      while (deletes.length > 0) {
        const deleteButton = deletes[0];

        if (await deleteButton.isVisible()) {
          await deleteButton.click({timeout: 60000});

          await expect(this.dialog).toBeVisible({timeout: 40000});
          await this.deleteAPIClientSubButton.click();
          await expect(this.page.getByText('Deleting...')).not.toBeVisible({
            timeout: 60000,
          });
        }
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(3000);
        deletes = await this.deleteAPIClientButton.all();
      }

      await expect(this.deleteAPIClientButton).not.toBeVisible({
        timeout: 30000,
      });
    } catch (error) {
      console.error('Error during API client deletion: ', error);
    }
  }

  async userTaskEnabledAssertion(): Promise<void> {
    try {
      await expect(
        await this.userTaskRestriction.getAttribute('aria-checked'),
      ).toBe('true');
    } catch (error) {
      await this.page.reload();
      await expect(this.userTaskRestriction.getAttribute('aria-checked')).toBe(
        'true',
      );
    }
  }
}

export {ClusterDetailsPage};
