/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from './BasePage';
import {TasklistHeader} from './TasklistHeader';
import {CustomFiltersModal, DeleteFilterModal, FilterNameModal} from './CustomFiltersModal';

class TasklistIndexPage extends BasePage {
	readonly header: TasklistHeader;
	readonly customFiltersModal: CustomFiltersModal;
	readonly filterNameModal: FilterNameModal;
	readonly deleteFilterModal: DeleteFilterModal;

	constructor(page: Page) {
		super(page);
		this.header = new TasklistHeader(page);
		this.customFiltersModal = new CustomFiltersModal(page);
		this.filterNameModal = new FilterNameModal(page);
		this.deleteFilterModal = new DeleteFilterModal(page);
	}

	async goto(search?: string) {
		return this.page.goto(`/tasklist${search ?? ''}`);
	}

	async seedCustomFilters(filters: Record<string, Record<string, unknown>>) {
		const serialized = JSON.stringify(filters);
		await this.page.addInitScript(`localStorage.setItem('tasklist.customFilters', ${JSON.stringify(serialized)})`);
	}

	get tasksPanel() {
		return this.page.getByRole('region', {name: 'Tasks side panel'});
	}

	get filterSelect() {
		return this.page.getByRole('button', {name: 'Filters', exact: true});
	}

	filterOption(name: string) {
		return this.page.getByRole('menuitem', {name, exact: true});
	}

	tasksPanelHeading(filterName: 'All open tasks' | 'Assigned to me' | 'Unassigned' | 'Completed' | (string & {})) {
		return this.filterSelect.filter({hasText: new RegExp(`^${filterName}$`)});
	}

	async expandFilters() {
		await this.filterSelect.click();
	}

	get newFilterButton() {
		return this.page.getByRole('menuitem', {name: 'New filter', exact: true});
	}

	get filterTasksButton() {
		return {
			click: async () => {
				await this.expandFilters();
				await this.newFilterButton.click();
			},
		};
	}

	customFilterLink(name: string) {
		return this.page
			.getByRole('menuitem', {name, exact: true})
			.or(this.filterSelect.filter({hasText: new RegExp(`^${name}$`)}))
			.last();
	}

	customFilterActionsButton(filterName: string) {
		return this.page.getByRole('menuitem', {name: `Custom filter actions - ${filterName}`, exact: true});
	}

	customFilterOverflowItem(name: 'Edit' | 'Delete') {
		return this.page.getByRole('menuitem', {name, exact: true});
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

export {TasklistIndexPage};
