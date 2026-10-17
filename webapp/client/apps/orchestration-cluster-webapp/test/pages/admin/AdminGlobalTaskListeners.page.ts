/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage, View} from '#/pages/BasePage';

class AddGlobalTaskListenerModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Add global task listener'});
	}

	get idInput() {
		return this.dialog.getByRole('textbox', {name: 'Listener ID'});
	}

	get typeInput() {
		return this.dialog.getByRole('textbox', {name: 'Listener type'});
	}

	get eventTypesCombobox() {
		return this.dialog.getByRole('combobox', {name: 'Event types'});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: 'Save'});
	}
}

class EditGlobalTaskListenerModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Edit global task listener'});
	}

	get typeInput() {
		return this.dialog.getByRole('textbox', {name: 'Listener type'});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: 'Save'});
	}
}

class DeleteGlobalTaskListenerModal extends View {
	get dialog() {
		return this.page.getByRole('alertdialog', {name: 'Delete global task listener'});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: 'Delete'});
	}
}

class AdminGlobalTaskListenersPage extends BasePage {
	readonly addModal: AddGlobalTaskListenerModal;
	readonly editModal: EditGlobalTaskListenerModal;
	readonly deleteModal: DeleteGlobalTaskListenerModal;

	constructor(page: Page) {
		super(page);
		this.addModal = new AddGlobalTaskListenerModal(page);
		this.editModal = new EditGlobalTaskListenerModal(page);
		this.deleteModal = new DeleteGlobalTaskListenerModal(page);
	}

	async goto() {
		return this.page.goto('/admin/global-task-listeners');
	}

	get addButton() {
		return this.page.getByRole('button', {name: 'Add global task listener'});
	}

	get searchInput() {
		return this.page.getByRole('searchbox');
	}

	row(name: string) {
		return this.page.getByRole('row', {name: new RegExp(name)});
	}

	rowActionsButton(name: string) {
		return this.row(name).getByRole('button', {name: /row actions/i});
	}

	menuItem(name: 'Edit' | 'Delete') {
		return this.page.getByRole('menuitem', {name, exact: true});
	}
}

export {AdminGlobalTaskListenersPage};
