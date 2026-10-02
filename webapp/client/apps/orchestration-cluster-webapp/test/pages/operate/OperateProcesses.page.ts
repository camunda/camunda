/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';
import {Header} from '#/pages/Header';

class OperateProcessesPage extends BasePage {
	readonly header: Header;

	constructor(page: Page) {
		super(page);
		this.header = new Header(page, 'Camunda Operate');
	}

	async goto(search = '') {
		return this.page.goto(`/operate/processes${search}`);
	}

	get filtersPanel() {
		return this.page.getByRole('region', {name: 'Filter'});
	}

	get processCombobox() {
		return this.page.getByRole('combobox', {name: 'Name'});
	}

	get elementCombobox() {
		return this.page.getByRole('combobox', {name: 'Element'});
	}

	get versionCombobox() {
		return this.page.getByRole('combobox', {name: 'Version'});
	}

	diagramElement(elementId: string) {
		return this.page.locator(`[data-element-id="${elementId}"]`);
	}

	get resetFiltersButton() {
		return this.page.getByRole('button', {name: 'Reset filters'});
	}

	get instancesTable() {
		return this.page.getByTestId('process-instances-table');
	}

	get operationStateColumn() {
		return this.instancesTable.getByRole('columnheader', {name: 'Operation State'});
	}

	operationState(state: string) {
		return this.instancesTable.getByRole('cell', {name: state});
	}

	get variableFilterModal() {
		return this.page.getByRole('dialog', {name: 'Filter by variable'});
	}

	get variableConditionsList() {
		return this.page.getByRole('list', {name: 'Active variable filters'});
	}

	async addOptionalFilter(label: string) {
		await this.page.getByRole('button', {name: 'More Filters'}).click();
		await this.page.getByRole('menuitem', {name: label}).click();
	}

	async removeOptionalFilter(label: string) {
		await this.filtersPanel.getByRole('heading', {name: label}).hover();
		await this.page.getByRole('button', {name: `Remove ${label} Filter`}).click();
	}

	instanceLink(processInstanceKey: string) {
		return this.page.getByRole('link', {name: `View instance ${processInstanceKey}`});
	}
}

export {OperateProcessesPage};
