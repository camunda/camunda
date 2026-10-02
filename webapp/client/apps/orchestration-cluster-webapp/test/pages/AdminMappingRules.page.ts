/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage, View} from './BasePage';

class AddMappingRuleModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Add mapping rule'});
	}

	get mappingRuleIdInput() {
		return this.dialog.getByRole('textbox', {name: 'Mapping rule ID'});
	}

	get nameInput() {
		return this.dialog.getByRole('textbox', {name: 'Name', exact: true});
	}

	get claimNameInput() {
		return this.dialog.getByRole('textbox', {name: 'Claim name'});
	}

	get claimValueInput() {
		return this.dialog.getByRole('textbox', {name: 'Claim value'});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: 'Save'});
	}
}

class EditMappingRuleModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Edit mapping rule'});
	}

	get nameInput() {
		return this.dialog.getByRole('textbox', {name: 'Name', exact: true});
	}

	get claimNameInput() {
		return this.dialog.getByRole('textbox', {name: 'Claim name'});
	}

	get claimValueInput() {
		return this.dialog.getByRole('textbox', {name: 'Claim value'});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: 'Save'});
	}
}

class DeleteMappingRuleModal extends View {
	get dialog() {
		return this.page.getByRole('alertdialog', {name: 'Delete mapping rule'});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: 'Delete'});
	}
}

class AdminMappingRulesPage extends BasePage {
	readonly addModal: AddMappingRuleModal;
	readonly editModal: EditMappingRuleModal;
	readonly deleteModal: DeleteMappingRuleModal;

	constructor(page: Page) {
		super(page);
		this.addModal = new AddMappingRuleModal(page);
		this.editModal = new EditMappingRuleModal(page);
		this.deleteModal = new DeleteMappingRuleModal(page);
	}

	async goto() {
		return this.page.goto('/admin/mapping-rules');
	}

	get addButton() {
		return this.page.getByRole('button', {name: 'Add mapping rule'});
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

export {AdminMappingRulesPage};
