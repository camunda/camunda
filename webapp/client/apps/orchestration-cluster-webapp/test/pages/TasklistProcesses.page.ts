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

class TasklistProcessesPage extends BasePage {
	readonly header: Header;

	constructor(page: Page) {
		super(page);
		this.header = new Header(page);
	}

	async goto(search = '') {
		return this.page.goto(`/tasklist/processes${search}`);
	}

	async gotoStartForm(processDefinitionKey: string, search = '') {
		return this.page.goto(`/tasklist/processes/${processDefinitionKey}/start${search}`);
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

	get tasksNavItem() {
		return this.page.getByRole('link', {name: 'Tasks'});
	}

	get processesNavItem() {
		return this.page.getByRole('link', {name: 'Processes'});
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

	get unpublishedProcessesHeading() {
		return this.page.getByRole('heading', {name: 'No published processes yet'});
	}

	get noMatchingProcessesHeading() {
		return this.page.getByRole('heading', {name: 'We could not find any process with that name'});
	}

	get loadMoreButton() {
		return this.page.getByRole('button', {name: 'Load more'});
	}

	get genericErrorHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}

	processHeading(name: string) {
		return this.page.getByRole('heading', {name});
	}

	get startProcessButton() {
		return this.page.getByRole('button', {name: 'Start process'});
	}

	get waitingForTasksStatus() {
		return this.page.getByText('Waiting for tasks...');
	}

	get startProcessDialog() {
		return this.page.getByRole('dialog', {name: /Start process/});
	}

	get cancelStartProcessButton() {
		return this.startProcessDialog.getByRole('button', {name: 'Cancel'});
	}

	get closeStartProcessButton() {
		return this.startProcessDialog.getByRole('button', {name: 'Close'});
	}

	get shareStartProcessButton() {
		return this.startProcessDialog.getByRole('button', {name: 'Share process URL'});
	}

	get startProcessFormButton() {
		return this.startProcessDialog.getByRole('button', {name: 'Start process'});
	}

	get startProcessFormError() {
		return this.startProcessDialog.getByRole('alert');
	}
}

export {TasklistProcessesPage};
