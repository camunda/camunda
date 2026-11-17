/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';

class OperatePreviewBatchOperationsPage extends BasePage {
	readonly batchOperationsUrl = '/operate-preview/batch-operations';

	constructor(page: Page) {
		super(page);
	}

	async goto() {
		return this.page.goto(this.batchOperationsUrl);
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Batch Operations', level: 1});
	}

	get table() {
		return this.page.getByTestId('batch-operations-table');
	}

	get emptyState() {
		return this.page.getByText('No batch operations found');
	}

	get pageIndicator() {
		return this.page.getByText('Page 1 of 3');
	}

	cellByText(text: string) {
		return this.page.getByRole('cell', {name: text});
	}
}

export {OperatePreviewBatchOperationsPage};
