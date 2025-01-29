import {Page, Locator, expect} from '@playwright/test';

class ClusterSecretsPage {
  private page: Page;
  readonly createNewSecretButton: Locator;
  readonly keyInput: Locator;
  readonly valueInput: Locator;
  readonly createButton: Locator;
  readonly operationsButton: Locator;
  readonly deleteConnectorSecretButton: Locator;
  readonly deleteConnectorSecretSubButton: Locator;
  readonly dialog: Locator;

  constructor(page: Page) {
    this.page = page;
    this.createNewSecretButton = page.getByRole('button', {
      name: 'Create new secret',
    });
    this.keyInput = page.getByLabel('Key');
    this.valueInput = page.getByLabel('Value', {exact: true});
    this.createButton = page.getByRole('button', {name: 'Create', exact: true});
    this.operationsButton = page.getByRole('button', {
      name: 'operations',
      exact: true,
    });
    this.deleteConnectorSecretButton = page.getByRole('menuitem', {
      name: 'delete',
    });
    this.deleteConnectorSecretSubButton = page.getByRole('button', {
      name: 'danger Delete',
    });
    this.dialog = page.getByRole('dialog');
  }

  async deleteConnectorSecretsIfExist(): Promise<void> {
    try {
      await expect(this.operationsButton.first()).toBeVisible({
        timeout: 10000,
      });
    } catch (error) {
      return; //No Connector Secrets found in the list
    }

    try {
      let operations = await this.operationsButton.all();
      while (operations.length > 0) {
        const operationButton = operations[0];

        if (await operationButton.isVisible()) {
          await operationButton.click({timeout: 60000});
          await this.deleteConnectorSecretButton.click({timeout: 60000});

          await expect(this.dialog).toBeVisible({timeout: 40000});
          await this.deleteConnectorSecretSubButton.click();
          await expect(this.page.getByText('Deleting...')).not.toBeVisible({
            timeout: 60000,
          });
        }
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(3000);
        operations = await this.operationsButton.all();
      }

      await expect(this.operationsButton).not.toBeVisible({
        timeout: 30000,
      });
    } catch (error) {
      console.error('Error during Connector Secrets deletion: ', error);
    }
  }

  async clickCreateNewSecretButton(): Promise<void> {
    try {
      await this.createNewSecretButton.click({timeout: 60000});
    } catch (error) {
      await this.page
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
