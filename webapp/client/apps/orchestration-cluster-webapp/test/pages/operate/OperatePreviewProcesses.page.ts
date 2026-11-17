/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';

class OperatePreviewProcessesPage extends BasePage {
	readonly processesUrl = '/operate-preview/processes';

	constructor(page: Page) {
		super(page);
	}

	async goto(search = '') {
		return this.page.goto(`${this.processesUrl}${search}`);
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Processes'});
	}

	get nameFilter() {
		return this.page.getByLabel('Name');
	}

	get diagramPanel() {
		return this.page.getByRole('region', {name: 'Diagram Panel'});
	}

	get instancesTable() {
		return this.page.getByTestId('process-instances-table');
	}
}

export {OperatePreviewProcessesPage};
