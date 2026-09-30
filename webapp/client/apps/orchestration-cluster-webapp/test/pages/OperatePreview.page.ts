/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from './BasePage';

class OperatePreviewPage extends BasePage {
	constructor(page: Page) {
		super(page);
	}

	async goto() {
		return this.page.goto('/operate-preview');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Dashboard'});
	}

	get metricPanel() {
		return this.page.getByTestId('metric-panel');
	}

	get processesByNameTile() {
		return this.page.getByText('Process instances by name');
	}

	get incidentsByErrorTile() {
		return this.page.getByText('Process incidents by error message');
	}

	processesByNameRow(name: string) {
		return this.page.getByText(name, {exact: false});
	}

	incidentsByErrorRow(errorMessage: string) {
		return this.page.getByText(errorMessage, {exact: false});
	}

	get noInstancesEmptyState() {
		return this.page.getByRole('heading', {name: 'No running process instances'});
	}

	get noInstancesLearnMoreLink() {
		return this.page.getByRole('link', {name: 'Learn more about Operate'});
	}

	get noInstancesModelerButton() {
		return this.page.getByRole('link', {name: 'Go to Modeler'});
	}
}

export {OperatePreviewPage};
