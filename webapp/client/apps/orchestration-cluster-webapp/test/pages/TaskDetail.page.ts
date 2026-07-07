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

	async goto(userTaskKey: string, search?: string) {
		return this.page.goto(`/tasklist/${userTaskKey}${search ?? ''}`);
	}

	async gotoProcess(userTaskKey: string) {
		return this.page.goto(`/tasklist/${userTaskKey}/process`);
	}

	async gotoHistory(userTaskKey: string, search?: string) {
		return this.page.goto(`/tasklist/${userTaskKey}/history${search ?? ''}`);
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

	get historyLoadError() {
		return this.page.getByText('Something went wrong');
	}

	get historyForbiddenError() {
		return this.page.getByText("You don't have permission to view task history");
	}

	get historyRetryButton() {
		return this.page.getByRole('button', {name: 'Try again'});
	}

	historyColumnHeader(name: RegExp | string) {
		return this.historyTabContent.getByRole('columnheader', {name});
	}

	get completionLabel() {
		return this.page.getByTestId('completion-label');
	}

	get notificationBannerAction() {
		return this.page.getByRole('button', {name: /Turn on notifications/});
	}

	get assignButton() {
		return this.page.getByRole('button', {name: /Assign to me/i});
	}

	get unassignButton() {
		return this.page.getByRole('button', {name: /^Unassign$/i});
	}

	get assigningStatus() {
		return this.page.getByText('Assigning...');
	}

	get unassigningStatus() {
		return this.page.getByText('Unassigning...');
	}

	get assignmentSuccessful() {
		return this.page.getByText('Assignment successful');
	}

	get unassignmentSuccessful() {
		return this.page.getByText('Unassignment successful');
	}

	get completeTaskButton() {
		return this.page.getByRole('button', {name: /^Complete Task$/i});
	}

	get autoSelectNextTaskSwitch() {
		return this.page.getByRole('switch', {name: 'Auto-select first available task'});
	}

	get completingTaskStatus() {
		return this.page.getByText('Completing task...');
	}

	get completionFailed() {
		return this.page.getByText('Completion failed');
	}

	taskName(name: string) {
		return this.page.getByText(name);
	}

	processName(name: string) {
		return this.processTabContent.getByText(name);
	}

	processVersion(version: number) {
		return this.processTabContent.getByText(`Version: ${version}`);
	}

	get processDiagramZoomReset() {
		return this.processTabContent.getByRole('button', {name: 'Reset diagram zoom'});
	}

	get processDiagramZoomIn() {
		return this.processTabContent.getByRole('button', {name: 'Zoom in diagram'});
	}

	get processDiagramZoomOut() {
		return this.processTabContent.getByRole('button', {name: 'Zoom out diagram'});
	}

	get processForbiddenError() {
		return this.processTabContent.getByText("You don't have permission to view the process");
	}

	get processLoadError() {
		return this.processTabContent.getByText('Process could not be loaded');
	}

	get processRetryButton() {
		return this.processTabContent.getByRole('button', {name: 'Try again'});
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
