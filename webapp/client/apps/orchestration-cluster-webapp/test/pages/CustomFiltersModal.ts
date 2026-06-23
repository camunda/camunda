/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {View} from './BasePage';

type AssigneeOption = 'All' | 'Unassigned' | 'Me' | 'User and group';
type StatusOption = 'All' | 'Open' | 'Completed';

class CustomFiltersModal extends View {
	constructor(page: Page) {
		super(page);
	}

	get dialog() {
		return this.page.getByRole('dialog', {name: /custom filters modal/i});
	}

	get heading() {
		return this.dialog.getByRole('heading', {name: /apply filters/i});
	}

	get assigneeGroup() {
		return this.dialog.getByRole('group', {name: /assignee/i});
	}

	get statusGroup() {
		return this.dialog.getByRole('group', {name: /status/i});
	}

	get processSelect() {
		return this.dialog.getByRole('combobox', {name: /process/i});
	}

	processOption(name: string) {
		return this.processSelect.getByRole('option', {name: new RegExp(name, 'i')});
	}

	statusRadio(name: StatusOption) {
		return this.statusGroup.getByLabel(name, {exact: true});
	}

	assigneeRadio(name: AssigneeOption) {
		return this.assigneeGroup.getByLabel(name, {exact: true});
	}

	get applyButton() {
		return this.dialog.getByRole('button', {name: /^apply$/i});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: /^save$/i});
	}

	get cancelButton() {
		return this.dialog.getByRole('button', {name: /cancel/i});
	}

	async selectStatus(option: StatusOption) {
		await this.statusGroup.getByText(option, {exact: true}).click();
	}

	async selectAssignee(option: AssigneeOption) {
		await this.assigneeGroup.getByText(option, {exact: true}).click();
	}

	async apply() {
		await this.applyButton.click();
	}

	async save() {
		await this.saveButton.click();
	}
}

class FilterNameModal extends View {
	constructor(page: Page) {
		super(page);
	}

	get dialog() {
		return this.page.getByRole('dialog', {name: /save/i});
	}

	get nameInput() {
		return this.page.getByRole('textbox', {name: /filter name/i});
	}

	get saveAndApplyButton() {
		return this.page.getByRole('button', {name: /save and apply/i});
	}

	async fillName(name: string) {
		await this.nameInput.fill(name);
	}

	async saveAndApply() {
		await this.saveAndApplyButton.click();
	}
}

class DeleteFilterModal extends View {
	constructor(page: Page) {
		super(page);
	}

	get dialog() {
		return this.page.getByRole('dialog', {name: /delete filter/i});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: /confirm deletion/i});
	}

	async confirm() {
		await this.confirmButton.click();
	}
}

export {CustomFiltersModal, FilterNameModal, DeleteFilterModal};
