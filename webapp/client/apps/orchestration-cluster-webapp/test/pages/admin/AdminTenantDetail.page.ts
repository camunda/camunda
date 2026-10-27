/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage, View} from '#/pages/BasePage';

class AssignMembersModal extends View {
	private readonly title: string;

	constructor(page: Page, title: string) {
		super(page);
		this.title = title;
	}

	get dialog() {
		return this.page.getByRole('dialog', {name: this.title});
	}

	get combobox() {
		return this.dialog.getByRole('combobox');
	}

	get textInput() {
		return this.dialog.getByRole('textbox');
	}

	get assignButton() {
		return this.dialog.getByRole('button', {name: 'Assign', exact: true});
	}
}

class UnassignModal extends View {
	get dialog() {
		return this.page.getByRole('alertdialog', {name: 'Remove from tenant'});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: 'Remove'});
	}
}

class AdminTenantDetailPage extends BasePage {
	readonly assignUserModal: AssignMembersModal;
	readonly assignClientModal: AssignMembersModal;
	readonly unassignModal: UnassignModal;

	constructor(page: Page) {
		super(page);
		this.assignUserModal = new AssignMembersModal(page, 'Assign user');
		this.assignClientModal = new AssignMembersModal(page, 'Assign client');
		this.unassignModal = new UnassignModal(page);
	}

	async goto(tenantId: string, tab?: string) {
		return this.page.goto(`/admin/tenants/${encodeURIComponent(tenantId)}${tab ? `?tab=${tab}` : ''}`);
	}

	get heading() {
		return this.page.getByRole('heading', {level: 1});
	}

	get editButton() {
		return this.page.getByRole('button', {name: 'Edit', exact: true});
	}

	get deleteButton() {
		return this.page.getByRole('button', {name: 'Delete', exact: true});
	}

	tab(name: string) {
		return this.page.getByRole('tab', {name});
	}

	assignButton(name: string) {
		return this.page.getByRole('button', {name});
	}

	row(name: string) {
		return this.page.getByRole('row', {name: new RegExp(name)});
	}

	rowActionsButton(name: string) {
		return this.row(name).getByRole('button', {name: /row actions/i});
	}

	get removeMenuItem() {
		return this.page.getByRole('menuitem', {name: 'Remove', exact: true});
	}
}

export {AdminTenantDetailPage};
