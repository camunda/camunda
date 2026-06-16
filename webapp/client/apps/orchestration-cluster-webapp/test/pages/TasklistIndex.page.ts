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

class TasklistIndexPage extends BasePage {
	readonly header: Header;

	constructor(page: Page) {
		super(page);
		this.header = new Header(page, 'Camunda Tasklist');
	}

	async goto() {
		return this.page.goto('/tasklist');
	}

	tasksPanelHeading(filterName: 'All open tasks' | 'Assigned to me' | 'Unassigned' | 'Completed' | (string & {})) {
		return this.page.getByRole('heading', {name: filterName});
	}

	get tasksNavItem() {
		return this.page.getByRole('link', {name: 'Tasks', exact: true});
	}

	get processesNavItem() {
		return this.page.getByRole('link', {name: 'Processes'});
	}

	taskItem(name: string) {
		return this.page.getByRole('link', {name: new RegExp(`task.*:.*${name}`, 'i')});
	}

	get noTasksMessage() {
		return this.page.getByText('No tasks found');
	}

	get expandFiltersButton() {
		return this.page.getByRole('button', {name: 'Expand to show filters'});
	}

	get collapseFiltersButton() {
		return this.page.getByRole('button', {name: 'Collapse'});
	}

	get filterTasksButton() {
		return this.page.getByRole('button', {name: 'Filter tasks'});
	}

	get newFilterButton() {
		return this.page.getByRole('button', {name: 'New filter'});
	}

	get filterControlsNav() {
		return this.page.getByRole('navigation', {name: 'Filter controls'});
	}

	filterLink(name: 'All open tasks' | 'Assigned to me' | 'Unassigned' | 'Completed') {
		return this.page.getByRole('link', {name});
	}

	async expandFilters() {
		await this.expandFiltersButton.click();
	}

	get sortButton() {
		return this.page.getByRole('button', {name: 'Sort tasks'});
	}

	sortOption(name: 'Creation date' | 'Due date' | 'Follow-up date' | 'Completion date' | 'Priority') {
		return this.page.getByRole('menuitem', {name});
	}

	async openSortMenu() {
		await this.sortButton.click();
	}
}

export {TasklistIndexPage};
