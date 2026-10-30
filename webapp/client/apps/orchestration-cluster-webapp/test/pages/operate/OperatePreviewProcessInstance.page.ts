/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';

class OperatePreviewProcessInstancePage extends BasePage {
	constructor(page: Page) {
		super(page);
	}

	async goto(processInstanceId: string) {
		return this.page.goto(`/operate-preview/processes/${processInstanceId}`);
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Operate Process Instance'});
	}

	get headerPlaceholder() {
		return this.page.getByText('Header placeholder');
	}

	get diagramPanelPlaceholder() {
		return this.page.getByText('Diagram panel placeholder');
	}

	get bottomPanelPlaceholder() {
		return this.page.getByText('Bottom panel placeholder');
	}

	get incidentsTab() {
		return this.page.getByRole('tab', {name: 'Incidents'});
	}

	get variablesTab() {
		return this.page.getByRole('tab', {name: 'Variables'});
	}
}

export {OperatePreviewProcessInstancePage};
