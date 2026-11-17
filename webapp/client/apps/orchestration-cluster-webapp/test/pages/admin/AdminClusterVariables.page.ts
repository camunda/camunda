/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Locator, type Page} from '@playwright/test';
import {BasePage, View} from '#/pages/BasePage';

// Monaco's textbox is a div, which Playwright's `fill` rejects, so type into it via the keyboard.
async function replaceEditorValue(page: Page, editor: Locator, value: string) {
	// A real pointer click on the view lines gives Monaco time to take focus; focusing the hidden textbox races it.
	await editor.locator('xpath=ancestor::div[contains(@class, "monaco-editor")][1]').locator('.view-lines').click();
	await page.keyboard.press('ControlOrMeta+A');
	await page.keyboard.press('Backspace');
	await page.keyboard.type(value);
}

class AddClusterVariableModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Create cluster variable'});
	}

	get nameInput() {
		return this.dialog.getByRole('textbox', {name: 'Name', exact: true});
	}

	get tenantScopeRadio() {
		return this.dialog.getByRole('radio', {name: 'Tenant'});
	}

	get valueEditor() {
		return this.dialog.getByRole('textbox', {name: 'Value - Enter string or JSON'});
	}

	get createButton() {
		return this.dialog.getByRole('button', {name: 'Create', exact: true});
	}

	async fillValue(value: string) {
		await replaceEditorValue(this.page, this.valueEditor, value);
	}
}

class ViewClusterVariableModal extends View {
	get dialog() {
		return this.page.getByRole('dialog');
	}

	get valueEditor() {
		return this.dialog.getByRole('textbox', {name: 'Value'});
	}
}

class EditClusterVariableModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Edit cluster variable'});
	}

	get nameInput() {
		return this.dialog.getByRole('textbox', {name: 'Name', exact: true});
	}

	get valueEditor() {
		return this.dialog.getByRole('textbox', {name: 'Value', exact: true});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: 'Save'});
	}

	async fillValue(value: string) {
		await replaceEditorValue(this.page, this.valueEditor, value);
	}
}

class DeleteClusterVariableModal extends View {
	get dialog() {
		return this.page.getByRole('alertdialog', {name: 'Delete cluster variable'});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: 'Delete'});
	}
}

class AdminClusterVariablesPage extends BasePage {
	readonly addModal: AddClusterVariableModal;
	readonly viewModal: ViewClusterVariableModal;
	readonly editModal: EditClusterVariableModal;
	readonly deleteModal: DeleteClusterVariableModal;

	constructor(page: Page) {
		super(page);
		this.addModal = new AddClusterVariableModal(page);
		this.viewModal = new ViewClusterVariableModal(page);
		this.editModal = new EditClusterVariableModal(page);
		this.deleteModal = new DeleteClusterVariableModal(page);
	}

	async goto() {
		return this.page.goto('/admin/cluster-variables');
	}

	get addButton() {
		return this.page.getByRole('button', {name: 'Create cluster variable'});
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

	menuItem(name: 'View' | 'Edit' | 'Delete') {
		return this.page.getByRole('menuitem', {name, exact: true});
	}
}

export {AdminClusterVariablesPage};
