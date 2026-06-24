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
  readonly editGlobalTaskListenerButton: (rowName?: string) => Locator;
  readonly deleteGlobalTaskListenerButton: (rowName?: string) => Locator;

  readonly createGlobalTaskListenerModal: Locator;
  readonly closeCreateGlobalTaskListenerModal: Locator;
  readonly createGlobalTaskListenerIdField: Locator;
  readonly createGlobalTaskListenerTypeField: Locator;
  readonly createGlobalTaskListenerEventTypeToggle: Locator;
  readonly createGlobalTaskListenerEventTypeMenu: Locator;
  readonly createGlobalTaskListenerInlineError: Locator;
  readonly createGlobalTaskListenerModalCancelButton: Locator;
  readonly createGlobalTaskListenerModalCreateButton: Locator;

  readonly editGlobalTaskListenerModal: Locator;
  readonly closeEditGlobalTaskListenerModal: Locator;
  readonly editGlobalTaskListenerTypeField: Locator;
  readonly editGlobalTaskListenerModalCancelButton: Locator;
  readonly editGlobalTaskListenerModalUpdateButton: Locator;

  readonly deleteGlobalTaskListenerModal: Locator;
  readonly closeDeleteGlobalTaskListenerModal: Locator;
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
      name: /^Create (listener|global user task listener)$/,
    });

    this.editGlobalTaskListenerButton = (rowName) =>
      this.globalTaskListenersList
        .getByRole('row', {name: rowName})
        .getByLabel('Update user task listener');

    this.deleteGlobalTaskListenerButton = (rowName) =>
      this.globalTaskListenersList
        .getByRole('row', {name: rowName})
        .getByLabel('Delete');

    this.createGlobalTaskListenerModal = page.getByRole('dialog', {
      name: 'Create user task listener',
    });
    this.closeCreateGlobalTaskListenerModal =
      this.createGlobalTaskListenerModal.getByRole('button', {name: 'Close'});
    this.createGlobalTaskListenerIdField =
      this.createGlobalTaskListenerModal.getByRole('textbox', {
        name: 'Task listener ID',
      });
    this.createGlobalTaskListenerTypeField =
      this.createGlobalTaskListenerModal.getByRole('textbox', {
        name: 'Listener type',
      });
    this.createGlobalTaskListenerEventTypeToggle =
      this.createGlobalTaskListenerModal.locator(
        '#event-type-multiselect button',
      );
    this.createGlobalTaskListenerEventTypeMenu =
      this.createGlobalTaskListenerModal.locator(
        '#event-type-multiselect .cds--list-box__menu',
      );
    this.createGlobalTaskListenerInlineError =
      this.createGlobalTaskListenerModal.locator(
        '.cds--inline-notification--error',
      );
    this.createGlobalTaskListenerModalCancelButton =
      this.createGlobalTaskListenerModal.getByRole('button', {name: 'Cancel'});
    this.createGlobalTaskListenerModalCreateButton =
      this.createGlobalTaskListenerModal.getByRole('button', {name: 'Create'});

    this.editGlobalTaskListenerModal = page.getByRole('dialog', {
      name: 'Update user task listener',
    });
    this.closeEditGlobalTaskListenerModal =
      this.editGlobalTaskListenerModal.getByRole('button', {name: 'Close'});
    this.editGlobalTaskListenerTypeField =
      this.editGlobalTaskListenerModal.getByRole('textbox', {
        name: 'Listener type',
      });
    this.editGlobalTaskListenerModalCancelButton =
      this.editGlobalTaskListenerModal.getByRole('button', {name: 'Cancel'});
    this.editGlobalTaskListenerModalUpdateButton =
      this.editGlobalTaskListenerModal.getByRole('button', {name: 'Update'});

    this.deleteGlobalTaskListenerModal = page.getByRole('dialog', {
      name: 'Delete user task listener',
    });
    this.closeDeleteGlobalTaskListenerModal =
      this.deleteGlobalTaskListenerModal.getByRole('button', {name: 'Close'});
    this.deleteGlobalTaskListenerModalCancelButton =
      this.deleteGlobalTaskListenerModal.getByRole('button', {name: 'Cancel'});
    this.deleteGlobalTaskListenerModalDeleteButton =
      this.deleteGlobalTaskListenerModal.getByRole('button', {
        name: /^(danger )?Delete$/,
      });

    this.emptyStateLocator = page.getByText(
      'No global user task listeners created yet',
    );
  }

  async navigateToGlobalTaskListeners() {
    await this.page.goto(relativizePath(Paths.globalTaskListeners()));
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
    await this.createGlobalTaskListenerTypeField.blur();
    await this.createGlobalTaskListenerEventTypeToggle.click();
    await expect(this.createGlobalTaskListenerEventTypeMenu).toBeVisible();
    await this.page
      .locator('[role="option"]', {hasText: eventTypeLabel})
      .click();
    await this.createGlobalTaskListenerModalCreateButton.click();
    await expect(this.createGlobalTaskListenerModal).toBeHidden();
  }

  async editGlobalTaskListener(
    currentListenerId: string,
    newType: string,
    newEventTypeLabel?: string,
  ) {
    await this.editGlobalTaskListenerButton(currentListenerId).click();
    await expect(this.editGlobalTaskListenerModal).toBeVisible();
    await this.editGlobalTaskListenerTypeField.clear();
    await this.editGlobalTaskListenerTypeField.fill(newType);
    if (newEventTypeLabel) {
      await this.editGlobalTaskListenerTypeField.blur();
      const editEventTypeToggle = this.editGlobalTaskListenerModal.locator(
        '#event-type-multiselect-edit button',
      );
      await editEventTypeToggle.click();
      const editEventTypeMenu = this.editGlobalTaskListenerModal.locator(
        '#event-type-multiselect-edit .cds--list-box__menu',
      );
      await expect(editEventTypeMenu).toBeVisible();
      await this.page
        .locator('[role="option"]', {hasText: newEventTypeLabel})
        .click();
    }
    await this.editGlobalTaskListenerModalUpdateButton.click();
    await expect(this.editGlobalTaskListenerModal).toBeHidden();
  }

  async deleteGlobalTaskListener(listenerId: string) {
    await expect(this.deleteGlobalTaskListenerButton(listenerId)).toBeVisible({
      timeout: 20000,
    });
    await expect(async () => {
      await this.deleteGlobalTaskListenerButton(listenerId).click();
    }).toPass(defaultAssertionOptions);
    await expect(this.deleteGlobalTaskListenerModal).toBeVisible();
    await this.deleteGlobalTaskListenerModalDeleteButton.click();
    await expect(this.deleteGlobalTaskListenerModal).toBeHidden();
  }
}
