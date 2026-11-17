/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from '#/pages/BasePage';

class AdminOperationsLogPage extends BasePage {
	async goto() {
		return this.page.goto('/admin/operations-log');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Operations Log', exact: true});
	}

	get table() {
		return this.page.getByRole('table', {name: 'Operations Log'});
	}

	get operationTypeFilter() {
		return this.page.getByLabel('Operation type');
	}

	get entityTypeFilter() {
		return this.page.getByLabel('Entity type');
	}

	get ownerTypeFilter() {
		return this.page.getByLabel('Owner type');
	}

	get ownerKeyFilter() {
		return this.page.getByLabel('Owner ID');
	}

	get statusFilter() {
		return this.page.getByLabel('Status');
	}

	get actorFilter() {
		return this.page.getByLabel('Actor');
	}

	get resetButton() {
		return this.page.getByRole('button', {name: 'Reset filters'});
	}

	sortButton(columnName: string) {
		return this.page.getByRole('button', {name: columnName, exact: true});
	}

	get pageSizeSelect() {
		return this.page.getByRole('combobox').last();
	}

	get nextPageButton() {
		return this.page.getByRole('button', {name: 'Next page'});
	}

	columnHeader(name: string) {
		return this.table.locator('th').filter({hasText: name});
	}

	row(text: string) {
		return this.table.getByRole('row').filter({hasText: text});
	}

	cell(text: string) {
		return this.table.getByRole('cell', {name: text});
	}

	get loadFailureHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}
}

export {AdminOperationsLogPage};
