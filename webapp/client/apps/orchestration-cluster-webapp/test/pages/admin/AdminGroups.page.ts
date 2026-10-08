/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from '#/pages/BasePage';

class AdminGroupsPage extends BasePage {
	async goto() {
		return this.page.goto('/admin/groups');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Groups', exact: true});
	}

	get searchField() {
		return this.page.getByRole('searchbox');
	}

	get table() {
		return this.page.getByRole('table', {name: 'Groups'});
	}

	get groupIdSortButton() {
		return this.page.getByRole('button', {name: 'Group ID'});
	}

	get groupNameSortButton() {
		return this.page.getByRole('button', {name: 'Group name'});
	}

	get createGroupButton() {
		return this.page.getByRole('button', {name: 'Create group', exact: true}).first();
	}

	get groupIdCells() {
		return this.table.locator('tbody tr td:first-child');
	}

	row(groupId: string) {
		return this.table.getByRole('row').filter({hasText: groupId});
	}

	cell(text: string) {
		return this.table.getByRole('cell', {name: text, exact: true});
	}

	rowActionsButton(groupId: string) {
		return this.row(groupId).getByRole('button', {name: 'Row actions'});
	}

	async editGroup(groupId: string) {
		await this.rowActionsButton(groupId).click();
		await this.page.getByRole('menuitem', {name: 'Edit group'}).click();
	}

	async deleteGroup(groupId: string) {
		await this.rowActionsButton(groupId).click();
		await this.page.getByRole('menuitem', {name: 'Delete group'}).click();
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

export {AdminGroupsPage};
