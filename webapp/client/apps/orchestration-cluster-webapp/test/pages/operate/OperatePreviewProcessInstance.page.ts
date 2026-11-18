/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';
import {variableRowName} from '#/shared-test-modules/variable-row';

class OperatePreviewProcessInstancePage extends BasePage {
	constructor(page: Page) {
		super(page);
	}

	async goto(processInstanceId: string, path = '') {
		return this.page.goto(`/operate-preview/processes/${processInstanceId}${path}`);
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Operate Process Instance'});
	}

	get header() {
		return this.page.getByRole('group', {name: 'Instance header'});
	}

	headerText(text: string) {
		return this.header.getByText(text);
	}

	get actionsButton() {
		return this.header.getByRole('button', {name: 'Actions'});
	}

	get breadcrumb() {
		return this.page.getByRole('navigation', {name: 'breadcrumb'});
	}

	breadcrumbLink(name: string) {
		return this.breadcrumb.getByRole('link', {name});
	}

	get breadcrumbMoreButton() {
		return this.breadcrumb.getByRole('button', {name: 'More'});
	}

	get menu() {
		return this.page.getByRole('menu');
	}

	menuItem(name: string | RegExp) {
		return this.menu.getByRole('menuitem', {name});
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

	get historyPanel() {
		return this.page.getByRole('region', {name: 'Instance History'});
	}

	get historyTree() {
		return this.historyPanel.getByRole('tree', {name: 'Instance History'});
	}

	historyItem(name: string | RegExp) {
		return this.historyTree.getByRole('treeitem', {name});
	}

	searchResultItem(name: string | RegExp) {
		return this.historyPanel.getByRole('tree', {name: 'Search results'}).getByRole('treeitem', {name});
	}

	get variablesList() {
		return this.page.getByRole('table', {name: 'Variable List'});
	}

	variableRow(name: string) {
		return this.variablesList.getByRole('row', {name: variableRowName(name)});
	}

	get incidentsList() {
		return this.page.getByRole('region', {name: 'Incidents list'});
	}

	get incidentsTable() {
		return this.incidentsList.getByRole('table');
	}

	get incidentsTab() {
		return this.page.getByRole('tab', {name: 'Incidents'});
	}

	get listenersList() {
		return this.page.getByRole('table', {name: 'Listeners List'});
	}

	get elementInstanceDetailsList() {
		return this.page.getByRole('table', {name: 'Element Instance Details'});
	}

	get operationsLogTable() {
		return this.page.getByRole('table', {name: 'Operations Log List'});
	}

	get tabPanel() {
		return this.page.getByRole('tabpanel');
	}

	tab(name: string) {
		return this.page.getByRole('tab', {name});
	}

	get variablesTab() {
		return this.page.getByRole('tab', {name: 'Variables'});
	}
}

export {OperatePreviewProcessInstancePage};
