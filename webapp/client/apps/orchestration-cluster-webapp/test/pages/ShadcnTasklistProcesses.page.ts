/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from './BasePage';
import {ShadcnHeader} from './ShadcnHeader';

class ShadcnTasklistProcessesPage extends BasePage {
	readonly header: ShadcnHeader;

	constructor(page: Page) {
		super(page);
		this.header = new ShadcnHeader(page);
	}

	async goto(search = '') {
		return this.page.goto(`/shadcn/tasklist/processes${search}`);
	}

	async gotoStartForm(processDefinitionKey: string, search = '') {
		return this.page.goto(`/shadcn/tasklist/processes/${processDefinitionKey}/start${search}`);
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Processes', exact: true});
	}

	get firstTimeWarningDialog() {
		return this.page.getByRole('dialog', {name: 'Start your process on demand'});
	}

	get continueFromFirstTimeWarningButton() {
		return this.firstTimeWarningDialog.getByRole('button', {name: 'Continue'});
	}

	get cancelFirstTimeWarningButton() {
		return this.firstTimeWarningDialog.getByRole('button', {name: 'Cancel'});
	}

	get genericErrorHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}

	get description() {
		return this.page.getByText('Browse and run processes published by your organization.');
	}

	get startProcessButton() {
		return this.page.getByRole('button', {name: 'Start process'});
	}

	get startProcessDialog() {
		return this.page.getByRole('dialog', {name: /Start process/});
	}

	get cancelStartProcessButton() {
		return this.startProcessDialog.getByRole('button', {name: 'Cancel'});
	}

	get startProcessFormButton() {
		return this.startProcessDialog.getByRole('button', {name: 'Start process'});
	}

	get startProcessFormError() {
		return this.startProcessDialog.getByRole('alert');
	}

	get waitingForTasksStatus() {
		return this.page.getByText('Waiting for tasks...');
	}

	get searchInput() {
		return this.page.getByRole('searchbox', {name: 'Search processes'});
	}

	get processFilter() {
		return this.page.getByRole('combobox', {name: 'Filter processes'});
	}

	get tenantFilter() {
		return this.page.getByRole('combobox', {name: 'Tenant'});
	}

	tenantFilterOption(option: string) {
		return this.page.getByRole('option', {name: option});
	}

	get unpublishedProcessesHeading() {
		return this.page.getByRole('heading', {name: 'No published processes yet'});
	}

	get noMatchingProcessesHeading() {
		return this.page.getByRole('heading', {name: 'We could not find any process with that name'});
	}

	get loadMoreButton() {
		return this.page.getByRole('button', {name: 'Load more'});
	}

	processHeading(name: string) {
		return this.page.getByRole('heading', {name});
	}

	processTile(processDefinitionKey: string) {
		return this.page.getByTestId(`process-tile-${processDefinitionKey}`);
	}

	processDefinitionId(processDefinitionKey: string, processDefinitionId: string) {
		return this.processTile(processDefinitionKey).getByText(processDefinitionId);
	}

	requiresFormPill(processDefinitionKey: string) {
		return this.processTile(processDefinitionKey).getByText('Requires form input');
	}

	async selectProcessFilter(
		option: 'All Processes' | 'Requires form input to start' | 'Does not require form input to start',
	) {
		await this.processFilter.click();
		await this.page.getByRole('option', {name: option}).click();
	}

	async selectTenant(option: string) {
		await this.tenantFilter.click();
		await this.tenantFilterOption(option).click();
	}
}

export {ShadcnTasklistProcessesPage};
