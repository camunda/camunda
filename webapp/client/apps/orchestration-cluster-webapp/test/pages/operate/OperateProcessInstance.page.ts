/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from '#/pages/BasePage';
import {Notifications} from '#/pages/Notifications';
import type {Page} from '@playwright/test';

class OperateProcessInstancePage extends BasePage {
	readonly notifications: Notifications;

	constructor(page: Page) {
		super(page);
		this.notifications = new Notifications(page);
	}

	async goto(key: string, suffix = '', prefix = '') {
		return this.page.goto(`${prefix}/operate/processes/${key}${suffix}`);
	}

	action(label: string, key: string) {
		return this.page.getByRole('button', {name: `${label} Instance ${key}`});
	}

	get actionsMenu() {
		return this.page.getByRole('button', {name: 'Actions'});
	}

	get confirmation() {
		return this.page.getByRole('dialog');
	}

	get apply() {
		return this.confirmation.getByRole('button', {name: 'Apply'});
	}

	get delete() {
		return this.confirmation.getByRole('button', {name: 'Delete', exact: true});
	}

	get historyTree() {
		return this.page.getByRole('region', {name: 'Instance History', exact: true});
	}

	get historyTab() {
		return this.page.getByRole('link', {name: 'Instance History', exact: true});
	}

	get timestamps() {
		return this.page.getByRole('switch', {name: 'End date', exact: true});
	}

	get executionCount() {
		return this.page.getByRole('switch', {name: 'Execution count', exact: true});
	}

	get historyRetry() {
		return this.page.getByRole('button', {name: 'Try again', exact: true});
	}
}

export {OperateProcessInstancePage};
