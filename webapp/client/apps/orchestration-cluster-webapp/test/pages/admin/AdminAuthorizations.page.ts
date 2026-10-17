/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage, View} from '#/pages/BasePage';
import {createAdminClientConfigScript} from '#/shared-test-modules/api-mocks/authorizations';

class AddAuthorizationModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: 'Create authorization'});
	}

	get ownerTypeSelect() {
		return this.dialog.getByRole('combobox', {name: 'Owner type'});
	}

	get usernameInput() {
		return this.dialog.getByRole('textbox', {name: 'Username'});
	}

	get resourceIdInput() {
		return this.dialog.getByRole('textbox', {name: 'Resource ID'});
	}

	permissionCheckbox(name: string) {
		return this.dialog.getByRole('checkbox', {name});
	}

	get createButton() {
		return this.dialog.getByRole('button', {name: 'Create', exact: true});
	}
}

class DeleteAuthorizationModal extends View {
	get dialog() {
		return this.page.getByRole('alertdialog', {name: 'Delete authorization'});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: 'Delete', exact: true});
	}
}

class AdminAuthorizationsPage extends BasePage {
	readonly addModal: AddAuthorizationModal;
	readonly deleteModal: DeleteAuthorizationModal;

	constructor(page: Page) {
		super(page);
		this.addModal = new AddAuthorizationModal(page);
		this.deleteModal = new DeleteAuthorizationModal(page);
	}

	// MSW's Playwright integration skips `.js` URLs as static assets, so the script is stubbed via `page.route`.
	async mockClientConfig(script = createAdminClientConfigScript()) {
		await this.page.route('**/admin/config.js', (route) =>
			route.fulfill({status: 200, contentType: 'text/javascript', body: script}),
		);
	}

	async goto(search = '') {
		return this.page.goto(`/admin/authorizations${search}`);
	}

	get addButton() {
		return this.page.getByRole('button', {name: 'Create authorization'});
	}

	get resourceTypeSelect() {
		return this.page.getByRole('combobox', {name: 'Resource type'});
	}

	resourceTypeOption(name: string) {
		return this.page.getByRole('option', {name, exact: true});
	}

	get ownerSearchInput() {
		return this.page.getByRole('searchbox');
	}

	get loadingSkeleton() {
		return this.page.getByTestId('authorizations-skeleton');
	}

	get emptyState() {
		return this.page.getByText('No authorizations found.');
	}

	row(text: string) {
		return this.page.getByRole('row', {name: new RegExp(text)});
	}

	deleteButton(text: string) {
		return this.row(text).getByRole('button', {name: 'Delete authorization'});
	}
}

export {AdminAuthorizationsPage};
