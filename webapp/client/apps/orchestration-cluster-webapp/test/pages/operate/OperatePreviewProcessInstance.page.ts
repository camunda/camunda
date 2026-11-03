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

	get header() {
		return this.page.getByRole('group', {name: 'Instance header'});
	}

	get breadcrumb() {
		return this.page.getByRole('navigation', {name: 'breadcrumb'});
	}

	get diagramPanel() {
		return this.page.getByRole('region', {name: 'Process instance diagram'});
	}

	get diagram() {
		return this.diagramPanel.getByRole('region', {name: 'Diagram', exact: true});
	}

	stateOverlay(name: string) {
		return this.diagramPanel.getByRole('img', {name, exact: true});
	}

	diagramElement(elementId: string) {
		return this.diagramPanel.locator(`[data-element-id="${elementId}"]`);
	}

	get variablesList() {
		return this.page.getByRole('table', {name: 'Variable List'});
	}

	variableRow(name: string) {
		return this.variablesList.getByRole('row', {name: new RegExp(`^${name}(?![\\w-])`)});
	}

	get incidentsTable() {
		return this.page.getByRole('table', {name: 'Incidents list'});
	}

	get incidentsTab() {
		return this.page.getByRole('tab', {name: 'Incidents'});
	}

	get variablesTab() {
		return this.page.getByRole('tab', {name: 'Variables'});
	}
}

export {OperatePreviewProcessInstancePage};
