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

class TaskDetailPage extends BasePage {
	readonly header: Header;

	constructor(page: Page) {
		super(page);
		this.header = new Header(page, 'Camunda Tasklist');
	}

	async goto(userTaskKey: string) {
		return this.page.goto(`/tasklist/${userTaskKey}`);
	}

	get skeleton() {
		return this.page.getByTestId('details-skeleton');
	}

	get detailsInfo() {
		return this.page.getByTestId('details-info');
	}

	get taskTab() {
		return this.page.getByRole('link', {name: 'Show task', exact: true});
	}

	get processTab() {
		return this.page.getByRole('link', {name: 'Show associated BPMN process'});
	}

	get historyTab() {
		return this.page.getByRole('link', {name: 'Show task history'});
	}

	get aside() {
		return this.page.getByRole('complementary', {name: 'Task details right panel'});
	}

	get taskTabContent() {
		return this.page.getByTestId('task-tab-content');
	}

	get processTabContent() {
		return this.page.getByTestId('process-tab-content');
	}

	get historyTabContent() {
		return this.page.getByTestId('history-tab-content');
	}

	get completionLabel() {
		return this.page.getByTestId('completion-label');
	}

	get notificationBannerAction() {
		return this.page.getByRole('button', {name: /Turn on notifications/});
	}

	taskName(name: string) {
		return this.page.getByText(name);
	}

	async seedHideNotificationBanner() {
		await this.page.addInitScript(
			`localStorage.setItem('tasklist.areNativeNotificationsEnabled', JSON.stringify(false))`,
		);
	}

	async seedShowNotificationBanner() {
		await this.page.addInitScript(`
			window.Notification = {permission: 'default', requestPermission: () => Promise.resolve('default')};
		`);
	}
}

export {TaskDetailPage};
