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

class ShadcnCustomFiltersModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: /apply filters/i});
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
		return this.dialog.getByRole('combobox', {name: 'Tasks for latest process version'});
	}

	processOption(name: string) {
		return this.page.getByRole('option', {name, exact: true});
	}

	get assignedToInput() {
		return this.dialog.getByRole('textbox', {name: /assigned to user/i});
	}

	statusRadio(name: StatusOption) {
		return this.statusGroup.getByRole('radio', {name, exact: true});
	}

	statusOption(name: StatusOption) {
		return this.statusRadio(name);
	}

	assigneeOption(name: AssigneeOption) {
		return this.assigneeGroup.getByRole('radio', {name, exact: true});
	}

	get advancedFiltersToggle() {
		return this.dialog.getByRole('switch', {name: /advanced filters/i});
	}

	get businessIdField() {
		return this.dialog.getByRole('textbox', {name: /business id/i});
	}

	get applyButton() {
		return this.dialog.getByRole('button', {name: /^apply$/i});
	}

	get saveButton() {
		return this.dialog.getByRole('button', {name: /^save$/i});
	}
}

class ShadcnFilterNameModal extends View {
	get dialog() {
		return this.page.getByRole('dialog', {name: /save filter/i});
	}

	get nameInput() {
		return this.dialog.getByRole('textbox', {name: /filter name/i});
	}

	get saveAndApplyButton() {
		return this.dialog.getByRole('button', {name: /save and apply/i});
	}
}

class ShadcnDeleteFilterModal extends View {
	get dialog() {
		return this.page.getByRole('alertdialog', {name: /delete filter/i});
	}

	get confirmButton() {
		return this.dialog.getByRole('button', {name: /confirm deletion/i});
	}
}

export {ShadcnCustomFiltersModal, ShadcnFilterNameModal, ShadcnDeleteFilterModal};
