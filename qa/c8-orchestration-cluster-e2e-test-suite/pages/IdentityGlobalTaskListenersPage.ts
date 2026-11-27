/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {relativizePath, Paths} from 'utils/relativizePath';
import {defaultAssertionOptions} from '../utils/constants';

export class IdentityGlobalTaskListenersPage {
  private page: Page;
  readonly globalTaskListenersList: Locator;
  readonly createGlobalTaskListenerButton: Locator;
  readonly rowActionsButton: (rowName: string) => Locator;
  readonly editGlobalTaskListenerMenuItem: Locator;
  readonly deleteGlobalTaskListenerMenuItem: Locator;

  readonly createGlobalTaskListenerModal: Locator;
  readonly createGlobalTaskListenerIdField: Locator;
  readonly createGlobalTaskListenerTypeField: Locator;
  readonly createGlobalTaskListenerEventTypesCombobox: Locator;
  readonly createGlobalTaskListenerErrorToast: Locator;
  readonly createGlobalTaskListenerModalCancelButton: Locator;
  readonly createGlobalTaskListenerModalSaveButton: Locator;

  readonly editGlobalTaskListenerModal: Locator;
  readonly editGlobalTaskListenerTypeField: Locator;
  readonly editGlobalTaskListenerEventTypesCombobox: Locator;
  readonly editGlobalTaskListenerModalCancelButton: Locator;
  readonly editGlobalTaskListenerModalSaveButton: Locator;

  readonly deleteGlobalTaskListenerModal: Locator;
  readonly deleteGlobalTaskListenerModalCancelButton: Locator;
  readonly deleteGlobalTaskListenerModalDeleteButton: Locator;

  readonly emptyStateLocator: Locator;
  readonly globalTaskListenerCell: (name: string) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.globalTaskListenersList = page.getByRole('table');

    this.globalTaskListenerCell = (name) =>
      this.globalTaskListenersList.getByRole('cell', {name, exact: true});

    this.createGlobalTaskListenerButton = page.getByRole('button', {
      name: /^(Add|Create) global task listener$/,
    });

    this.rowActionsButton = (rowName) =>
      this.globalTaskListenersList
        .getByRole('row')
        .filter({hasText: rowName})
        .getByRole('button', {name: 'Row actions'});
    this.editGlobalTaskListenerMenuItem = page.getByRole('menuitem', {
      name: 'Edit',
      exact: true,
    });
    this.deleteGlobalTaskListenerMenuItem = page.getByRole('menuitem', {
      name: 'Delete',
      exact: true,
    });

    this.createGlobalTaskListenerModal = page.getByRole('dialog', {
      name: 'Add global task listener',
      exact: true,
    });
    this.createGlobalTaskListenerIdField =
      this.createGlobalTaskListenerModal.getByRole('textbox', {
        name: 'Listener ID',
      });
    this.createGlobalTaskListenerTypeField =
      this.createGlobalTaskListenerModal.getByRole('textbox', {
        name: 'Listener type',
      });
    this.createGlobalTaskListenerEventTypesCombobox =
      this.createGlobalTaskListenerModal.getByRole('combobox', {
        name: 'Event types',
      });
    // The server rejection (e.g. duplicate ID) is reported as a toast, not inline.
    this.createGlobalTaskListenerErrorToast = page.getByText(
      'Failed to create global task listener',
    );
    this.createGlobalTaskListenerModalCancelButton =
      this.createGlobalTaskListenerModal
        .getByRole('button', {name: 'Cancel'})
        .filter({hasText: 'Cancel'});
    this.createGlobalTaskListenerModalSaveButton =
      this.createGlobalTaskListenerModal.getByRole('button', {name: 'Save'});

    this.editGlobalTaskListenerModal = page.getByRole('dialog', {
      name: 'Edit global task listener',
      exact: true,
    });
    this.editGlobalTaskListenerTypeField =
      this.editGlobalTaskListenerModal.getByRole('textbox', {
        name: 'Listener type',
      });
    this.editGlobalTaskListenerEventTypesCombobox =
      this.editGlobalTaskListenerModal.getByRole('combobox', {
        name: 'Event types',
      });
    this.editGlobalTaskListenerModalCancelButton =
      this.editGlobalTaskListenerModal
        .getByRole('button', {name: 'Cancel'})
        .filter({hasText: 'Cancel'});
    this.editGlobalTaskListenerModalSaveButton =
      this.editGlobalTaskListenerModal.getByRole('button', {name: 'Save'});

    this.deleteGlobalTaskListenerModal = page.getByRole('alertdialog', {
      name: 'Delete global task listener',
      exact: true,
    });
    this.deleteGlobalTaskListenerModalCancelButton =
      this.deleteGlobalTaskListenerModal.getByRole('button', {name: 'Cancel'});
    this.deleteGlobalTaskListenerModalDeleteButton =
      this.deleteGlobalTaskListenerModal.getByRole('button', {
        name: 'Delete',
        exact: true,
      });

    this.emptyStateLocator = page.getByText(
      /^No global task listeners (found\.|created yet)$/,
    );
  }

  async navigateToGlobalTaskListeners() {
    await this.page.goto(relativizePath(Paths.globalTaskListeners()));
  }

  async selectEventType(combobox: Locator, eventTypeLabel: string) {
    await combobox.click();
    await this.page
      .getByRole('option', {name: eventTypeLabel, exact: true})
      .click();
    // The options list is a popup that overlays the Save button until closed.
    await this.page.keyboard.press('Escape');
  }

  async createGlobalTaskListener(
    listenerId: string,
    listenerType: string,
    eventTypeLabel: string,
  ) {
    await this.createGlobalTaskListenerButton.click();
    await expect(this.createGlobalTaskListenerModal).toBeVisible();
    await this.createGlobalTaskListenerIdField.fill(listenerId);
    await this.createGlobalTaskListenerTypeField.fill(listenerType);
    await this.selectEventType(
      this.createGlobalTaskListenerEventTypesCombobox,
      eventTypeLabel,
    );
    await this.createGlobalTaskListenerModalSaveButton.click();
    await expect(this.createGlobalTaskListenerModal).toBeHidden();
  }

  async editGlobalTaskListener(
    currentListenerId: string,
    newType: string,
    newEventTypeLabel?: string,
  ) {
    await this.rowActionsButton(currentListenerId).click();
    await this.editGlobalTaskListenerMenuItem.click();
    await expect(this.editGlobalTaskListenerModal).toBeVisible();
    await this.editGlobalTaskListenerTypeField.fill(newType);
    if (newEventTypeLabel) {
      await this.selectEventType(
        this.editGlobalTaskListenerEventTypesCombobox,
        newEventTypeLabel,
      );
    }
    await this.editGlobalTaskListenerModalSaveButton.click();
    await expect(this.editGlobalTaskListenerModal).toBeHidden();
  }

  async deleteGlobalTaskListener(listenerId: string) {
    await expect(this.rowActionsButton(listenerId)).toBeVisible({
      timeout: 20000,
    });
    await expect(async () => {
      await this.rowActionsButton(listenerId).click();
      await this.deleteGlobalTaskListenerMenuItem.click({timeout: 5000});
    }).toPass(defaultAssertionOptions);
    await expect(this.deleteGlobalTaskListenerModal).toBeVisible();
    await this.deleteGlobalTaskListenerModalDeleteButton.click();
    await expect(this.deleteGlobalTaskListenerModal).toBeHidden();
  }
}
