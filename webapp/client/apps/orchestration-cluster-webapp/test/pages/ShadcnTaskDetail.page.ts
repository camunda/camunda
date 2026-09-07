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

	async gotoHistory(userTaskKey: string, search?: string) {
		return this.page.goto(`/shadcn/tasklist/${userTaskKey}/history${search ?? ''}`);
	}

	async gotoHistoryDetails(userTaskKey: string, auditLogKey: string, search?: string) {
		return this.page.goto(`/shadcn/tasklist/${userTaskKey}/history/${auditLogKey}${search ?? ''}`);
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
		return this.processTabContent.getByText(name, {exact: true});
	}

	get detailsNavigation() {
		return this.page.getByRole('navigation', {name: 'Task Details Navigation', includeHidden: true});
	}

	get taskTab() {
		return this.detailsNavigation.getByRole('tab', {name: 'Show task', exact: true});
	}

	get processTab() {
		return this.detailsNavigation.getByRole('tab', {name: 'Show associated BPMN process', exact: true});
	}

	get historyTab() {
		return this.detailsNavigation.getByRole('tab', {name: 'Show task history', exact: true, includeHidden: true});
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
		return this.page.getByRole('tabpanel');
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
		return this.historyTabContent.getByRole('button', {name});
	}

	get historyDetailsModal() {
		return this.page.getByRole('dialog');
	}

	get historyDetailsCloseButton() {
		return this.historyDetailsModal.getByRole('button', {name: 'Close'});
	}

	get historyDetailsLink() {
		return this.historyTabContent.getByRole('link', {name: 'Open details'});
	}

	get assignButton() {
		return this.detailsHeader.getByRole('button', {name: 'Assign to me'});
	}

	get unassignButton() {
		return this.detailsHeader.getByRole('button', {name: 'Unassign'});
	}

	get completeTaskButton() {
		return this.page.getByRole('button', {name: /^Complete Task$/i});
	}

	get completionLabel() {
		return this.page.getByTestId('completion-label');
	}

	get variablesHeading() {
		return this.variablesTable.getByRole('columnheader', {name: 'Name', exact: true});
	}

	get variablesTable() {
		return this.page.getByTestId('variables-table');
	}

	get addVariableButton() {
		return this.page.getByRole('button', {name: 'Add Variable'});
	}

	variableValueInput(name: string) {
		return this.variablesTable.getByRole('textbox', {name: `${name} Value`, exact: true});
	}

	newVariableNameInput(ordinal: string) {
		return this.variablesTable.getByRole('textbox', {name: `${ordinal} variable name`, exact: true});
	}

	newVariableValueInput(ordinal: string) {
		return this.variablesTable.getByRole('textbox', {name: `${ordinal} variable value`, exact: true});
	}

	get firstNewVariableNameInput() {
		return this.newVariableNameInput('1st');
	}

	get firstNewVariableValueInput() {
		return this.newVariableValueInput('1st');
	}

	get firstNewVariableRemoveButton() {
		return this.variablesTable.getByRole('button', {name: 'Remove 1st new variable'});
	}

	get fillAllVariableFieldsWarning() {
		return this.page.getByRole('button', {name: 'You first have to fill all fields'});
	}

	get openJsonEditorButtons() {
		return this.variablesTable.getByRole('button', {name: 'Open JSON code editor'});
	}

	jsonEditorDialog(title: 'Edit Variable' | 'View Variable') {
		return this.page.getByRole('dialog', {name: title});
	}

	jsonEditorInput(title: 'Edit Variable' | 'View Variable') {
		return this.jsonEditorDialog(title).getByRole('textbox');
	}

	jsonEditorContent(title: 'Edit Variable' | 'View Variable', text: string) {
		return this.jsonEditorDialog(title).locator('.view-lines').getByText(text, {exact: false});
	}

	get applyJsonEditorButton() {
		return this.page.getByRole('button', {name: 'Apply'});
	}

	get invalidVariableValueError() {
		return this.variablesTable.getByRole('row').filter({hasText: 'Value has to be JSON or a literal'});
	}

	get missingVariableNameError() {
		return this.variablesTable.getByRole('row').filter({hasText: 'Name has to be filled'});
	}

	async replaceVariableValue(name: string, value: string) {
		const input = this.variableValueInput(name);
		await input.press('ControlOrMeta+a');
		await input.pressSequentially(value);
	}

	async replaceJsonEditorValue(value: string) {
		await this.jsonEditorInput('Edit Variable').pressSequentially(value);
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

	selectedTask(name: string) {
		return this.page
			.getByRole('region', {name: 'Tasks side panel'})
			.getByRole('link', {name: new RegExp(`task.*:.*${name}`, 'i')});
	}
}

export {ShadcnTaskDetailPage};
