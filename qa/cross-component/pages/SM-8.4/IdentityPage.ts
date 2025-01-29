import {Page, Locator} from '@playwright/test';

class IdentityPage {
  private page: Page;
  readonly identityBanner: Locator;
  readonly usersLink: Locator;
  readonly assignedRolesTab: Locator;
  readonly demoUser: Locator;
  readonly confirmDeleteButton: Locator;
  readonly assignRolesButton: Locator;
  readonly addButton: Locator;
  readonly operateCheckbox: Locator;
  readonly tasklistCheckbox: Locator;
  readonly optimizeCheckbox: Locator;
  readonly modelerCheckbox: Locator;
  readonly consoleCheckbox: Locator;
  readonly groupsTab: Locator;
  readonly createFirstGroupButton: Locator;
  readonly createGroupButton: Locator;
  readonly createGroupSubButton: Locator;
  readonly groupNameInput: Locator;
  readonly assignMembersButton: Locator;
  readonly assignMembersSubButton: Locator;
  readonly assignMembersSearchbox: Locator;
  readonly assignMembersSearchResult: Locator;
  readonly noGoupsHeading: Locator;
  readonly deleteButton: Locator;
  readonly deleteSubButton: Locator;
  readonly createGroupDialoge: Locator;

  constructor(page: Page) {
    this.page = page;
    this.identityBanner = page.getByRole('link', {name: 'Camunda Identity'});
    this.usersLink = page.getByRole('link', {name: 'Users'});
    this.assignedRolesTab = page.getByRole('tab', {name: 'Assigned roles'});
    this.demoUser = page.getByRole('cell', {name: 'demo'}).first();
    this.confirmDeleteButton = page.getByRole('button', {
      name: 'danger Delete',
    });
    this.assignRolesButton = page.getByRole('button', {name: 'Assign roles'});
    this.addButton = page.getByRole('button', {name: 'Add'});
    this.operateCheckbox = page
      .locator('label')
      .filter({hasText: 'Operate (Grants full access to Operate)'});
    this.tasklistCheckbox = page
      .locator('label')
      .filter({hasText: 'Tasklist (Grants full access to Tasklist)'});
    this.optimizeCheckbox = page
      .locator('label')
      .filter({hasText: 'Optimize (Grants full access to Optimize)'});
    this.modelerCheckbox = page
      .locator('label')
      .filter({hasText: 'Web Modeler (Grants full access to Web Modeler)'});
    this.consoleCheckbox = page
      .locator('label')
      .filter({hasText: 'Console (Grants full access to Console)'});
    this.groupsTab = page.getByText('groups').first();
    this.createGroupButton = page.getByRole('button', {
      name: 'create group',
    });
    this.createFirstGroupButton = page.getByRole('button', {
      name: 'create a group',
    });
    this.createGroupSubButton = page
      .getByRole('button', {
        name: 'create group',
      })
      .last();
    this.groupNameInput = page.getByRole('textbox', {name: 'name'});
    this.assignMembersButton = page.getByRole('button', {
      name: 'assign members',
    });
    this.assignMembersSubButton = page
      .getByRole('button', {
        name: 'assign',
      })
      .last();
    this.assignMembersSearchbox = page.getByRole('searchbox', {
      name: 'search by full name or email address',
    });
    this.assignMembersSearchResult = page.locator('.cds--list-box__menu-item');
    this.noGoupsHeading = page.getByRole('heading', {
      name: 'you don’t have any groups yet',
    });
    this.deleteSubButton = page.getByRole('button', {name: 'danger delete'});
    this.deleteButton = page.locator('button', {hasText: 'danger'});
    this.createGroupDialoge = page.getByRole('dialog', {
      name: 'create group',
    });
  }

  async clickUsersLink(): Promise<void> {
    try {
      await this.usersLink.click({timeout: 180000, force: true});
    } catch {
      await this.page.reload();
      await this.usersLink.click({timeout: 180000, force: true});
    }
  }

  async clickAssignedRolesTab(): Promise<void> {
    await this.assignedRolesTab.click();
  }

  async clickDemoUser(): Promise<void> {
    await this.demoUser.click({timeout: 180000});
  }

  async clickDeleteAccessButton(accessToDelete: string): Promise<void> {
    try {
      await this.page
        .locator('td:right-of(:text("' + accessToDelete + '"))')
        .first()
        .click({timeout: 60000});
    } catch {
      console.log('Access already deleted');
    }
  }

  async clickConfirmDeleteButton(): Promise<void> {
    try {
      await this.confirmDeleteButton.click();
    } catch {
      console.log('Access already deleted');
    }
  }

  async clickAssignRolesButton(): Promise<void> {
    await this.assignRolesButton.click();
  }

  async clickAddButton(): Promise<void> {
    await this.addButton.click();
  }

  async clickOperateCheckbox(): Promise<void> {
    await this.operateCheckbox.click();
  }

  async clickTasklistCheckbox(): Promise<void> {
    await this.tasklistCheckbox.click();
  }

  async clickOptimizeCheckbox(): Promise<void> {
    await this.optimizeCheckbox.click();
  }

  async clickModelerCheckbox(): Promise<void> {
    await this.modelerCheckbox.click();
  }

  async clickConsoleCheckbox(): Promise<void> {
    await this.consoleCheckbox.click();
  }

  async clickGroupsTab(): Promise<void> {
    await this.groupsTab.click({timeout: 120000});
  }

  async clickCreateGroupButton(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if ((await this.noGoupsHeading.isVisible({timeout: 120000})) == false) {
          await this.createGroupButton.click();
        } else {
          await this.createFirstGroupButton.click();
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed`);
      }
    }
    throw new Error(`Failed to click the button after ${maxRetries} attempts.`);
  }

  async fillGroupNameInput(name: string): Promise<void> {
    await this.groupNameInput.fill(name);
  }

  async clickUserGroup(name: string): Promise<void> {
    await this.page.getByRole('cell', {name: name}).click();
  }

  async clickAssignMembers(): Promise<void> {
    await this.assignMembersButton.click();
  }

  async fillAssignMembers(names: string[]): Promise<void> {
    for (const name of names) {
      await this.assignMembersSearchbox.click();
      await this.assignMembersSearchbox.fill(name);
      await this.assignMembersSearchResult.filter({hasText: name}).click();
    }
  }
  async clickAssignSubButton(): Promise<void> {
    await this.assignMembersSubButton.click();
  }

  async clickCreateGroupSubButton(): Promise<void> {
    await this.createGroupSubButton.click();
  }

  async deleteAllGroups(): Promise<void> {
    try {
      await this.deleteButton
        .first()
        .waitFor({state: 'visible', timeout: 120000});
      const count = await this.deleteButton.count();
      if (count > 0) {
        for (let i = 0; i < count; i++) {
          await this.deleteButton.nth(i).click({timeout: 60000});
          await this.deleteSubButton.click({timeout: 60000});
        }
      }
    } catch (error) {
      console.log('Delete Button Not Clicked ' + error);
    }
  }
}

export {IdentityPage};
