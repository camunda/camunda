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

class ShadcnTaskDetailPage extends BasePage {
	readonly header: ShadcnHeader;

	constructor(page: Page) {
		super(page);
		this.header = new ShadcnHeader(page);
	}

	async goto(userTaskKey: string, search?: string) {
		return this.page.goto(`/shadcn/tasklist/${userTaskKey}${search ?? ''}`);
	}

	async gotoProcess(userTaskKey: string) {
		return this.page.goto(`/shadcn/tasklist/${userTaskKey}/process`);
	}

	async gotoHistory(userTaskKey: string) {
		return this.page.goto(`/shadcn/tasklist/${userTaskKey}/history`);
	}

	get detailsInfo() {
		return this.page.getByTestId('details-info');
	}

	get detailsHeader() {
		return this.page.getByTitle('Task details header');
	}

	taskName(name: string) {
		return this.detailsHeader.getByText(name, {exact: true});
	}

	processName(name: string) {
		return this.detailsHeader.getByText(name, {exact: true});
	}

	get detailsNavigation() {
		return this.page.getByRole('navigation', {name: 'Task Details Navigation'});
	}

	get taskTab() {
		return this.detailsNavigation.getByRole('tab', {name: 'Show task', exact: true});
	}

	get processTab() {
		return this.detailsNavigation.getByRole('tab', {name: 'Show associated BPMN process', exact: true});
	}

	get historyTab() {
		return this.detailsNavigation.getByRole('tab', {name: 'Show task history', exact: true});
	}

	get aside() {
		return this.page.getByRole('complementary', {name: 'Task details right panel'});
	}

	get assignButton() {
		return this.detailsHeader.getByRole('button', {name: 'Assign to me'});
	}

	get unassignButton() {
		return this.detailsHeader.getByRole('button', {name: 'Unassign'});
	}

	get completeTaskButton() {
		return this.page.getByRole('button', {name: 'Complete task'});
	}

	get assignee() {
		return this.detailsHeader.getByTestId('assignee');
	}

	get assigningStatus() {
		return this.page.getByText('Assigning...');
	}

	get unassigningStatus() {
		return this.page.getByText('Unassigning...');
	}

	get completingTaskStatus() {
		return this.page.getByText('Completing task...');
	}

	get completionFailed() {
		return this.page.getByText('Completion failed');
	}

	async seedHideNotificationBanner() {
		await this.page.addInitScript(
			`localStorage.setItem('tasklist.areNativeNotificationsEnabled', JSON.stringify(false))`,
		);
	}

	selectedTask(name: string) {
		return this.page
			.getByRole('region', {name: 'Tasks side panel'})
			.getByRole('link', {name: new RegExp(`task.*:.*${name}`, 'i')});
	}
}

export {ShadcnTaskDetailPage};
