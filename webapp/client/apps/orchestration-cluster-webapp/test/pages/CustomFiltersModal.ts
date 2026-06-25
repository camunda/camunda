/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {View} from './BasePage';

type StatusOption = 'All' | 'Open' | 'Completed';
type AssigneeOption = 'All' | 'Unassigned' | 'Me' | 'User and group';

class CustomFiltersModal extends View {
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

	get assignedToInput() {
		return this.dialog.getByRole('textbox', {name: /assigned to user/i});
	}

	statusRadio(name: StatusOption) {
		return this.statusGroup.getByLabel(name, {exact: true});
	}

	statusOption(name: StatusOption) {
		return this.statusGroup.getByText(name, {exact: true});
	}

	assigneeOption(name: AssigneeOption) {
		return this.assigneeGroup.getByText(name, {exact: true});
	}

	get applyButton() {
		return this.dialog.getByRole('button', {name: /^apply$/i});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: /^save$/i});
	}
}

class FilterNameModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: /save filter/i});
	}

	get nameInput() {
		return this.page.getByRole('textbox', {name: /filter name/i});
	}

	get saveAndApplyButton() {
		return this.page.getByRole('button', {name: /save and apply/i});
	}
}

class DeleteFilterModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: /delete filter/i});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: /confirm deletion/i});
	}
}

export {CustomFiltersModal, FilterNameModal, DeleteFilterModal};
