/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';

class OperatePreviewDecisionsPage extends BasePage {
	readonly decisionsUrl = '/operate-preview/decisions';

	constructor(page: Page) {
		super(page);
	}

	async goto() {
		return this.page.goto(this.decisionsUrl);
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Decisions'});
	}

	get filtersPanelPlaceholder() {
		return this.page.getByText('Filters panel placeholder');
	}

	get decisionPanelPlaceholder() {
		return this.page.getByText('Decision panel placeholder');
	}

	get instancesTablePlaceholder() {
		return this.page.getByText('Instances table placeholder');
	}

	get panelResizeHandle() {
		return this.page.locator('.__dbk__dragger');
	}
}

export {OperatePreviewDecisionsPage};
