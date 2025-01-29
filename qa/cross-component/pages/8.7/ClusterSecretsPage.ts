import {Page, Locator} from '@playwright/test';

class ClusterSecretsPage {
  private page: Page;
  readonly createNewSecretButton: Locator;
  readonly keyInput: Locator;
  readonly valueInput: Locator;
  readonly createButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.createNewSecretButton = page.getByRole('button', {
      name: 'Create new secret',
    });
    this.keyInput = page.getByLabel('Key');
    this.valueInput = page.getByLabel('Value', {exact: true});
    this.createButton = page.getByRole('button', {name: 'Create', exact: true});
  }

  async clickCreateNewSecretButton(): Promise<void> {
    try {
      await this.createNewSecretButton.click({timeout: 60000});
    } catch (error) {
      await this.page
        .getByRole('banner')
        .getByRole('link', {name: 'Clusters'})
        .click({timeout: 60000});
      await this.page
        .getByRole('link', {name: 'Test Cluster'})
        .click({timeout: 60000});
      await this.page
        .getByRole('tab', {
          name: 'Connector Secrets',
        })
        .click({timeout: 60000});
      await this.createNewSecretButton.click({timeout: 60000});
    }
  }

  async clickKeyInput(): Promise<void> {
    await this.keyInput.click();
  }

  async fillKeyInput(key: string): Promise<void> {
    await this.keyInput.fill(key);
  }

  async clickValueInput(): Promise<void> {
    await this.valueInput.click();
  }

  async fillValueInput(value: string): Promise<void> {
    await this.valueInput.fill(value);
  }

  async clickCreateButton(): Promise<void> {
    await this.createButton.click();
  }
}

export {ClusterSecretsPage};
