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
		return this.page.getByText('Process Instances by Name');
	}

	get incidentsByErrorTile() {
		return this.page.getByText('Process Incidents by Error Message');
	}

	get listTileSkeletonRows() {
		return this.page.locator('[data-slot="data-table-skeleton-row"]');
	}
}

export {OperatePreviewPage};
