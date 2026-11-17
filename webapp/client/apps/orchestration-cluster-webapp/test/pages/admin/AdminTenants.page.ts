/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage, View} from '#/pages/BasePage';

class CreateTenantModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Create tenant'});
	}

	get tenantIdInput() {
		return this.dialog.getByRole('textbox', {name: 'Tenant ID'});
	}

	get nameInput() {
		return this.dialog.getByRole('textbox', {name: 'Name', exact: true});
	}

	get descriptionInput() {
		return this.dialog.getByRole('textbox', {name: 'Description'});
	}

	get createButton() {
		return this.dialog.getByRole('button', {name: 'Create', exact: true});
	}
}

class EditTenantModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Edit tenant'});
	}

	get nameInput() {
		return this.dialog.getByRole('textbox', {name: 'Name', exact: true});
	}

	get descriptionInput() {
		return this.dialog.getByRole('textbox', {name: 'Description'});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: 'Save'});
	}
}

class DeleteTenantModal extends View {
	get dialog() {
		return this.page.getByRole('alertdialog', {name: 'Delete tenant'});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: 'Delete'});
	}
}

class AdminTenantsPage extends BasePage {
	readonly createModal: CreateTenantModal;
	readonly editModal: EditTenantModal;
	readonly deleteModal: DeleteTenantModal;

	constructor(page: Page) {
		super(page);
		this.createModal = new CreateTenantModal(page);
		this.editModal = new EditTenantModal(page);
		this.deleteModal = new DeleteTenantModal(page);
	}

	async goto() {
		return this.page.goto('/admin/tenants');
	}

	get createButton() {
		return this.page.getByRole('button', {name: 'Create tenant'});
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

export {AdminTenantsPage};
