import {Page, Locator, expect} from '@playwright/test';
import {sleep} from '../../utils/sleep';

class ConsoleOrganizationPage {
  private page: Page;
  readonly usersTab: Locator;
  readonly optionsButton: Locator;
  readonly editUserMenuItem: Locator;
  readonly operationsEngineerCheckbox: Locator;
  readonly developerCheckbox: Locator;
  readonly analystCheckbox: Locator;
  readonly confirmButton: Locator;
  readonly settingsTab: Locator;
  readonly optInButton: Locator;
  readonly optInCheckbox: Locator;
  readonly newContextPadButton: Locator;
  readonly newContextPadButtonText: Locator;
  readonly mainUser: Locator;
  readonly authorizations: Locator;
  readonly noAuthorizedResourceMessage: Locator;
  readonly dialog: Locator;
  readonly deleteAuthorisedResourceButton: Locator;
  readonly deleteAuthorisedResourceSubButton: Locator;
  readonly createResourceAuthorizationButton: Locator;
  readonly createResourceAuthorizationDialog: Locator;
  readonly nextButton: Locator;
  readonly createButton: Locator;
  readonly idComboBox: Locator;
  readonly readPermissionCheckbox: Locator;
  readonly startProcessIPermissionCheckbox: Locator;
  readonly optInAICheckbox: Locator;
  readonly rows: Locator;
  readonly groupsTab: Locator;
  readonly createGroupButton: Locator;
  readonly createGroupSubButton: Locator;
  readonly groupNameInput: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usersTab = page.getByRole('tab', {name: 'Users'});
    this.optionsButton = page.getByRole('button', {name: 'Options'});
    this.editUserMenuItem = page.getByRole('menuitem', {name: 'Edit User'});
    this.operationsEngineerCheckbox = page
      .getByRole('group')
      .locator('label')
      .filter({hasText: 'Operations Engineer'});
    this.developerCheckbox = page
      .getByRole('group')
      .locator('label')
      .filter({hasText: 'Developer'});
    this.analystCheckbox = page
      .getByRole('group')
      .locator('label')
      .filter({hasText: 'Analyst'});
    this.confirmButton = page.getByRole('button', {name: 'Confirm'});
    this.settingsTab = page.getByRole('tab', {name: 'Settings'});
    this.optInButton = page.getByRole('button', {name: 'Opt-in', exact: true});
    this.optInCheckbox = page.getByLabel('I understand and agree to the');
    this.newContextPadButton = page.getByRole('switch').last();
    this.newContextPadButtonText = page
      .locator('[class= "cds--toggle__text"]')
      .last();
    this.mainUser = page
      .getByRole('row')
      .filter({hasText: process.env.C8_USERNAME!});
    this.authorizations = page.getByRole('tab', {
      name: 'Authorizations',
    });
    this.noAuthorizedResourceMessage = page.getByText(
      "You haven't created any resource authorizations yet",
      {exact: true},
    );
    this.dialog = page.getByRole('dialog');
    this.deleteAuthorisedResourceButton = page.getByRole('button', {
      name: 'Delete',
    });
    this.deleteAuthorisedResourceSubButton = page.getByRole('button', {
      name: 'danger Delete',
    });
    this.createResourceAuthorizationButton = page.getByRole('button', {
      name: 'Create resource authorization',
      exact: true,
    });
    this.createResourceAuthorizationDialog = page.getByRole('dialog', {
      name: 'Create resource authorizations',
      exact: true,
    });
    this.nextButton = this.createResourceAuthorizationDialog.getByRole(
      'button',
      {
        name: 'Next',
      },
    );
    this.createButton = this.createResourceAuthorizationDialog.getByRole(
      'button',
      {
        name: 'Create',
      },
    );
    this.idComboBox = page.getByRole('combobox', {name: 'ID'});
    this.readPermissionCheckbox = page.getByRole('checkbox', {
      name: 'Read definition',
    });
    this.startProcessIPermissionCheckbox = page.getByRole('checkbox', {
      name: 'Start process instance',
    });
    this.optInAICheckbox = page.getByLabel(
      'I understand and agree to Terms for AI Usage',
    );
    this.rows = this.page.getByRole('row');
    this.groupsTab = page.getByRole('tab', {name: 'Groups'});
    this.createGroupButton = page.getByRole('button', {
      name: 'create a group',
    });
    this.createGroupSubButton = page
      .getByRole('button', {
        name: 'create a group',
      })
      .last();
    this.groupNameInput = page.getByRole('textbox', {name: 'name'});
  }

  async deleteAuthorisedResourcesIfExist(): Promise<void> {
    try {
      await expect(this.deleteAuthorisedResourceButton.first()).toBeVisible({
        timeout: 10000,
      });
    } catch (error) {
      return; //No authorised resource found in the list
    }

    try {
      let deletes = await this.deleteAuthorisedResourceButton.all();
      while (deletes.length > 0) {
        const deleteButton = deletes[0];

        if (await deleteButton.isVisible()) {
          await deleteButton.click({timeout: 60000});

          await expect(this.dialog).toBeVisible({timeout: 40000});
          await this.deleteAuthorisedResourceSubButton.click();
          await expect(this.page.getByText('Deleting...')).not.toBeVisible({
            timeout: 60000,
          });
        }
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(3000);
        deletes = await this.deleteAuthorisedResourceButton.all();
      }

      await expect(this.deleteAuthorisedResourceButton).not.toBeVisible({
        timeout: 30000,
      });
    } catch (error) {
      console.error('Error during authorised resource deletion: ', error);
    }
  }

  async clickCreateAuthorizedResourceButton(): Promise<void> {
    await expect(this.createButton).toBeVisible({timeout: 60000});
    await this.createButton.click();
  }

  async processIdResourceAssertion(processId: string): Promise<void> {
    await expect(this.page.getByRole('row', {name: processId})).toBeVisible({
      timeout: 90000,
    });
  }

  async clickCreateResourceAuthorizationButton(): Promise<void> {
    await expect(this.createResourceAuthorizationButton).toBeVisible({
      timeout: 60000,
    });
    await this.createResourceAuthorizationButton.click();
  }

  async clickNextButton(): Promise<void> {
    await expect(this.nextButton).toBeVisible({
      timeout: 60000,
    });
    await this.nextButton.click();
  }

  async clickProcessIdInput(): Promise<void> {
    await expect(this.idComboBox).toBeVisible({timeout: 60000});
    await this.idComboBox.click();
  }

  async fillProcessIdInput(processId: string): Promise<void> {
    await this.idComboBox.fill(processId);
  }

  async checkReadPermissionCheckbox(): Promise<void> {
    await expect(this.readPermissionCheckbox).toBeVisible({timeout: 60000});
    if (!(await this.readPermissionCheckbox.isChecked())) {
      await this.readPermissionCheckbox.click({force: true});
    }
  }

  async checkStartInstancePermissionCheckbox(): Promise<void> {
    if (!(await this.startProcessIPermissionCheckbox.isChecked())) {
      await this.startProcessIPermissionCheckbox.click({force: true});
    }
  }

  async clickUsersTab(): Promise<void> {
    await this.usersTab.click({timeout: 60000});
  }

  async clickOptionsButton(): Promise<void> {
    await this.optionsButton.click({timeout: 60000});
  }

  async clickEditUserMenuItem(): Promise<void> {
    await this.editUserMenuItem.click({timeout: 60000});
  }

  async clickOperationsEngineerCheckbox(): Promise<void> {
    await this.operationsEngineerCheckbox.click();
  }

  async checkDeveloperCheckbox(): Promise<void> {
    if (!(await this.developerCheckbox.isChecked())) {
      await this.developerCheckbox.click({timeout: 60000});
    }
  }

  async uncheckDeveloperCheckbox(): Promise<void> {
    if (await this.developerCheckbox.isChecked()) {
      await this.developerCheckbox.click({timeout: 60000});
    }
  }

  async checkAnalystCheckbox(): Promise<void> {
    if (!(await this.analystCheckbox.isChecked())) {
      await this.analystCheckbox.click({timeout: 60000});
    }
  }

  async uncheckAnalystCheckbox(): Promise<void> {
    if (await this.analystCheckbox.isChecked()) {
      await this.analystCheckbox.click({timeout: 60000});
    }
  }

  async clickConfirmButton(): Promise<void> {
    await this.confirmButton.click({timeout: 60000});
  }

  async clickSettingsTab(): Promise<void> {
    await this.settingsTab.click({timeout: 120000});
    try {
      await expect(
        this.page.getByRole('heading', {name: 'Alpha features'}),
      ).toBeVisible({
        timeout: 120000,
      });
    } catch (error) {
      await this.settingsTab.click({timeout: 120000});
    }
  }

  async clickOptInButton(): Promise<void> {
    await this.optInButton.click({timeout: 60000});
  }

  async enableAlphaFeature(name: string): Promise<void> {
    await expect(this.rows.first()).toBeVisible({timeout: 10000});
    const alphaFeature = this.rows.filter({hasText: name}).first();
    if ((await alphaFeature.count()) < 1) {
      console.error(`No alpha feature(${name}) is found.`);
      return;
    }
    //Opt-in The Alpha Feature
    const alphaFeatureOptInButton = alphaFeature.getByRole('button', {
      name: 'Opt-in to enable',
    });
    if (await alphaFeatureOptInButton.isVisible({timeout: 10000})) {
      await alphaFeatureOptInButton.click();
      await this.optInAICheckbox.scrollIntoViewIfNeeded();
      await this.optInAICheckbox.check({timeout: 90000});
      await sleep(3000);
    }
    //Enable Alpha Feature
    const toggleText = await alphaFeature
      .locator('[class= "cds--toggle__text"]')
      .textContent();
    console.info(`Previous feature(${name}) setting is ${toggleText}.`);

    if (toggleText == 'Enabled') {
      console.log(`Feature ${name} is already enabled.`);
      return;
    }
    if (toggleText == 'Disabled') {
      await alphaFeature
        .locator('[class= "cds--toggle__switch"]')
        .first()
        .click();
      await sleep(10000);
    }

    await expect(
      alphaFeature.locator('[class= "cds--toggle__text"]'),
    ).toHaveText('Enabled');
  }

  async scrollToOptInCheckbox(): Promise<void> {
    await this.page
      .getByText('Access and Use of the Camunda')
      .scrollIntoViewIfNeeded();
    await this.page
      .getByText('Pre-release. The Customer')
      .scrollIntoViewIfNeeded();
    await this.page
      .getByText('Confidential Information. The')
      .scrollIntoViewIfNeeded();
    await this.page
      .getByText('THESE TERMS DO NOT ENTITLE')
      .scrollIntoViewIfNeeded();
    await this.page
      .getByText('Rights and Obligations Upon')
      .scrollIntoViewIfNeeded();
    await this.page
      .getByText('NOTWITHSTANDING ANYTHING TO')
      .scrollIntoViewIfNeeded();
    await this.page
      .getByRole('heading', {name: 'General'})
      .scrollIntoViewIfNeeded();
    await this.page
      .getByText('Liability. Camunda is liable')
      .scrollIntoViewIfNeeded();
    await this.page
      .getByLabel('I understand and agree to the')
      .scrollIntoViewIfNeeded();
  }

  async checkOptInCheckbox(): Promise<void> {
    await this.optInCheckbox.check({timeout: 90000});
  }

  async clickMainUser(): Promise<void> {
    await expect(this.mainUser).toBeVisible({timeout: 60000});
    await this.mainUser.click();
  }

  async clickAuthorizations(): Promise<void> {
    await expect(this.authorizations).toBeVisible({timeout: 60000});
    await this.authorizations.click();
  }

  async authorizedResourceAssertion(
    processId: string,
    maxRetries: number = 3,
    retryDelay: number = 20000,
  ): Promise<void> {
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await this.page.reload();
        await this.clickAuthorizations();
        await expect(this.page.getByRole('row', {name: processId})).toBeVisible(
          {timeout: 90000},
        );
        return;
      } catch (error) {
        if (attempt < maxRetries - 1) {
          console.warn(`Attempt ${attempt + 1} failed. Retrying...`);
          await new Promise((resolve) => setTimeout(resolve, retryDelay));
        } else {
          throw new Error(`Assertion failed after ${maxRetries} attempts`);
        }
      }
    }
  }

  async clickNewContextPadButton(): Promise<void> {
    try {
      await expect(this.newContextPadButtonText).toHaveText('Enabled', {
        timeout: 90000,
      });
    } catch {
      await this.page.reload();
      await this.newContextPadButton.click({timeout: 90000});
      await expect(this.newContextPadButtonText).toHaveText('Enabled', {
        timeout: 60000,
      });
    }
  }

  async optInToAlphaFeatures(): Promise<void> {
    try {
      await expect(this.optInButton).toBeVisible({timeout: 30000});
      await this.clickOptInButton();
      await this.scrollToOptInCheckbox();
      await this.checkOptInCheckbox();
    } catch {
      await expect(this.optInButton).not.toBeVisible();
    }
  }
}

export {ConsoleOrganizationPage};
