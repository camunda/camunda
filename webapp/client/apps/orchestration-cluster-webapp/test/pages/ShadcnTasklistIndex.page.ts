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

class ShadcnTasklistIndexPage extends BasePage {
	readonly header: ShadcnHeader;

	constructor(page: Page) {
		super(page);
		this.header = new ShadcnHeader(page);
	}

	async goto() {
		return this.page.goto('/shadcn/tasklist');
	}

	get tasksPanel() {
		return this.page.getByRole('region', {name: 'Tasks side panel'});
	}

	get filterSelect() {
		return this.page.getByRole('combobox', {name: 'Filters'});
	}

	filterOption(name: string) {
		return this.page.getByRole('option', {name});
	}

	get sortButton() {
		return this.page.getByRole('button', {name: 'Sort tasks'});
	}

	async openSortMenu() {
		await this.sortButton.click();
	}

	sortOption(name: string) {
		return this.page.getByRole('menuitemradio', {name});
	}

	get autoSelectToggle() {
		return this.page.getByRole('switch', {name: 'Auto-select first available task'});
	}

	taskItem(name: string) {
		return this.page.getByRole('link', {name: new RegExp(`task.*:.*${name}`, 'i')});
	}

	get noTasksMessage() {
		return this.page.getByRole('heading', {name: 'No tasks found'});
	}

	get welcomeHeading() {
		return this.page.getByRole('heading', {name: 'Welcome to Tasklist'});
	}
}

export {ShadcnTasklistIndexPage};
