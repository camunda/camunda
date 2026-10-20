/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from '#/pages/BasePage';
import {createAdminClientConfigScript} from '#/shared-test-modules/api-mocks/authorizations';

class AdminRolesPage extends BasePage {
	// MSW's Playwright integration skips `.js` URLs as static assets, so the script is stubbed via `page.route`.
	async mockClientConfig(script = createAdminClientConfigScript()) {
		await this.page.route('**/admin/config.js', (route) =>
			route.fulfill({status: 200, contentType: 'text/javascript', body: script}),
		);
	}

	async goto() {
		return this.page.goto('/admin/roles');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Roles', exact: true});
	}

	get searchField() {
		return this.page.getByRole('searchbox');
	}

	get table() {
		return this.page.getByRole('table', {name: 'Roles'});
	}

	get roleIdSortButton() {
		return this.page.getByRole('button', {name: 'Role ID'});
	}

	get roleNameSortButton() {
		return this.page.getByRole('button', {name: 'Role name'});
	}

	get createRoleButton() {
		return this.page.getByRole('button', {name: 'Create role', exact: true}).first();
	}

	get roleIdCells() {
		return this.table.locator('tbody tr td:first-child');
	}

	row(roleId: string) {
		return this.table.getByRole('row').filter({hasText: roleId});
	}

	cell(text: string) {
		return this.table.getByRole('cell', {name: text, exact: true});
	}

	rowActionsButton(roleId: string) {
		return this.row(roleId).getByRole('button', {name: 'Row actions'});
	}

	menuItem(name: string) {
		return this.page.getByRole('menuitem', {name, exact: true});
	}

	async editRole(roleId: string) {
		await this.rowActionsButton(roleId).click();
		await this.page.getByRole('menuitem', {name: 'Edit role'}).click();
	}

	async deleteRole(roleId: string) {
		await this.rowActionsButton(roleId).click();
		await this.page.getByRole('menuitem', {name: 'Delete role'}).click();
	}

	get loadFailureHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}

	get dialog() {
		return this.page.getByRole('dialog');
	}

	get alertDialog() {
		return this.page.getByRole('alertdialog');
	}

	dialogField(label: string) {
		return this.dialog.getByLabel(label, {exact: true});
	}

	dialogButton(name: string) {
		return this.dialog.getByRole('button', {name, exact: true});
	}

	alertDialogButton(name: string) {
		return this.alertDialog.getByRole('button', {name, exact: true});
	}
}

export {AdminRolesPage};
