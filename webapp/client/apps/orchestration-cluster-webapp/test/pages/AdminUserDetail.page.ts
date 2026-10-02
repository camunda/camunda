/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class AdminUserDetailPage extends BasePage {
	async goto(username: string) {
		return this.page.goto(`/admin/users/${username}`);
	}

	heading(username: string) {
		return this.page.getByRole('heading', {name: username, exact: true});
	}

	get editButton() {
		return this.page.getByRole('button', {name: 'Edit user', exact: true});
	}

	get deleteButton() {
		return this.page.getByRole('button', {name: 'Delete user', exact: true});
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

export {AdminUserDetailPage};
