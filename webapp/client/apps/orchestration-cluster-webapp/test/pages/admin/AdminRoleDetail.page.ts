/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from '#/pages/BasePage';
import {createAdminClientConfigScript} from '#/shared-test-modules/api-mocks/authorizations';

class AdminRoleDetailPage extends BasePage {
	// MSW's Playwright integration skips `.js` URLs as static assets, so the script is stubbed via `page.route`.
	async mockClientConfig(script = createAdminClientConfigScript()) {
		await this.page.route('**/admin/config.js', (route) =>
			route.fulfill({status: 200, contentType: 'text/javascript', body: script}),
		);
	}

	async goto(roleId: string, search = '') {
		return this.page.goto(`/admin/roles/${roleId}${search}`);
	}

	heading(name: string) {
		return this.page.getByRole('heading', {name, exact: true});
	}

	get editButton() {
		return this.page.getByRole('button', {name: 'Edit role', exact: true});
	}

	get deleteButton() {
		return this.page.getByRole('button', {name: 'Delete role', exact: true});
	}

	tab(name: string) {
		return this.page.getByRole('tab', {name, exact: true});
	}

	get tabs() {
		return this.page.getByRole('tab');
	}

	table(name: string) {
		return this.page.getByRole('table', {name});
	}

	cell(text: string) {
		return this.page.getByRole('cell', {name: text, exact: true});
	}

	button(name: string) {
		return this.page.getByRole('button', {name, exact: true});
	}

	option(name: string) {
		return this.page.getByRole('option', {name});
	}

	get dialogCombobox() {
		return this.dialog.getByRole('combobox');
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

export {AdminRoleDetailPage};
