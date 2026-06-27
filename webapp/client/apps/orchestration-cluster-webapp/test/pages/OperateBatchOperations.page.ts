/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from './BasePage';
import {Header} from './Header';

class OperateBatchOperationsPage extends BasePage {
	readonly header: Header;

	constructor(page: Page) {
		super(page);
		this.header = new Header(page, 'Camunda Operate');
	}

	async goto() {
		return this.page.goto('/operate/batch-operations');
	}

	get table() {
		return this.page.getByTestId('batch-operations-table');
	}

	get emptyState() {
		return this.page.getByText('No batch operations found');
	}

	get pagination() {
		return this.page.getByRole('combobox', {name: 'Items per page:'});
	}

	cellByText(text: string) {
		return this.page.getByRole('cell', {name: text});
	}
}

export {OperateBatchOperationsPage};
