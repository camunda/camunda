import {Page, Locator} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';

export class IdentityMappingsPage {
  private page: Page;
  readonly mappingsList: Locator;
  readonly createMappingButton: Locator;
  readonly editMappingButton: (rowName?: string) => Locator;
  readonly deleteMappingButton: (rowName?: string) => Locator;
  readonly createMappingModal: Locator;
  readonly closeCreateMappingModal: Locator;
  readonly createMappingIdField: Locator;
  readonly createMappingNameField: Locator;
  readonly createMappingClaimNameField: Locator;
  readonly createMappingClaimValueField: Locator;
  readonly createMappingModalCancelButton: Locator;
  readonly createMappingModalCreateButton: Locator;
  readonly editMappingModal: Locator;
  readonly closeEditMappingModal: Locator;
  readonly editMappingIdField: Locator;
  readonly editMappingNameField: Locator;
  readonly editMappingClaimNameField: Locator;
  readonly editMappingClaimValueField: Locator;
  readonly editMappingModalCancelButton: Locator;
  readonly editMappingModalUpdateButton: Locator;
  readonly deleteMappingModal: Locator;
  readonly closeDeleteMappingModal: Locator;
  readonly deleteMappingModalCancelButton: Locator;
  readonly deleteMappingModalDeleteButton: Locator;
  readonly emptyState: Locator;
  readonly usersNavItem: Locator;

  constructor(page: Page) {
    this.page = page;
    this.mappingsList = page.getByRole('table');
    this.createMappingButton = page.getByRole('button', {
      name: 'Create a mapping',
    });
    this.editMappingButton = (rowName) =>
      this.mappingsList.getByRole('row', {name: rowName}).getByLabel('Edit');
    this.deleteMappingButton = (rowName) =>
      this.mappingsList.getByRole('row', {name: rowName}).getByLabel('Delete');

    this.createMappingModal = page.getByRole('dialog', {
      name: 'Create new mapping',
    });
    this.closeCreateMappingModal = this.createMappingModal.getByRole('button', {
      name: 'Close',
    });
    this.createMappingIdField = this.createMappingModal.getByRole('textbox', {
      name: 'Mapping ID',
    });
    this.createMappingNameField = this.createMappingModal.getByRole('textbox', {
      name: 'Mapping name',
    });
    this.createMappingClaimNameField = this.createMappingModal.getByRole(
      'textbox',
      {name: 'Claim name'},
    );
    this.createMappingClaimValueField = this.createMappingModal.getByRole(
      'textbox',
      {name: 'Claim value'},
    );
    this.createMappingModalCancelButton = this.createMappingModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.createMappingModalCreateButton = this.createMappingModal.getByRole(
      'button',
      {name: 'Create a mapping'},
    );

    this.editMappingModal = page.getByRole('dialog', {
      name: 'Edit mapping',
    });
    this.closeEditMappingModal = this.editMappingModal.getByRole('button', {
      name: 'Close',
    });
    this.editMappingIdField = this.editMappingModal.getByRole('textbox', {
      name: 'Mapping ID',
    });
    this.editMappingNameField = this.editMappingModal.getByRole('textbox', {
      name: 'Mapping name',
    });
    this.editMappingClaimNameField = this.editMappingModal.getByRole(
      'textbox',
      {name: 'Claim name'},
    );
    this.editMappingClaimValueField = this.editMappingModal.getByRole(
      'textbox',
      {name: 'Claim value'},
    );
    this.editMappingModalCancelButton = this.editMappingModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.editMappingModalUpdateButton = this.editMappingModal.getByRole(
      'button',
      {name: 'Update mapping'},
    );

    this.deleteMappingModal = page.getByRole('dialog', {
      name: 'Delete mapping',
    });
    this.closeDeleteMappingModal = this.deleteMappingModal.getByRole('button', {
      name: 'Close',
    });
    this.deleteMappingModalCancelButton = this.deleteMappingModal.getByRole(
      'button',
      {name: 'Cancel'},
    );
    this.deleteMappingModalDeleteButton = this.deleteMappingModal.getByRole(
      'button',
      {name: 'Delete mapping'},
    );

    this.emptyState = page.getByText("You don't have any mappings yet");
    this.usersNavItem = page.getByText('Users');
  }

  async navigateToMappings() {
    await this.page.goto(relativizePath(Paths.mappings()));
  }
}
