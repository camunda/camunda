/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class AdminUsersPage extends BasePage {
	async goto() {
		return this.page.goto('/admin/users');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Users', exact: true});
	}

	get searchField() {
		return this.page.getByRole('searchbox');
	}

	get table() {
		return this.page.getByRole('table', {name: 'Users'});
	}

	get usernameSortButton() {
		return this.page.getByRole('button', {name: 'Username'});
	}

	get nameSortButton() {
		return this.page.getByRole('button', {name: 'Name', exact: true});
	}

	get emailSortButton() {
		return this.page.getByRole('button', {name: 'Email'});
	}

	get pageSizeSelect() {
		return this.page.getByRole('combobox');
	}

	get createUserButton() {
		return this.page.getByRole('button', {name: 'Create user', exact: true}).first();
	}

	get loadFailureHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}

	get usernameCells() {
		return this.table.locator('tbody tr td:first-child');
	}

	row(username: string) {
		return this.table.getByRole('row').filter({hasText: username});
	}

	cell(text: string) {
		return this.table.getByRole('cell', {name: text, exact: true});
	}

	rowActionsButton(username: string) {
		return this.row(username).getByRole('button', {name: 'Row actions'});
	}

	async editUser(username: string) {
		await this.rowActionsButton(username).click();
		await this.page.getByRole('menuitem', {name: 'Edit user'}).click();
	}

	async deleteUser(username: string) {
		await this.rowActionsButton(username).click();
		await this.page.getByRole('menuitem', {name: 'Delete user'}).click();
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

export {AdminUsersPage};
