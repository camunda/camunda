/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {Notifications} from './Notifications';
import {View} from './BasePage';

class Header extends View {
	readonly notifications: Notifications;

	constructor(page: Page) {
		super(page);
		this.notifications = new Notifications(page);
	}

	get branding() {
		return this.page.getByRole('link', {name: 'Camunda logo'});
	}

	environmentBadge(stage: string) {
		return this.page.getByRole('banner').getByText(`Environment: ${stage}`, {exact: true});
	}

	get breadcrumbs() {
		return this.page.getByRole('navigation', {name: 'Camunda context'});
	}

	get userMenuButton() {
		return this.page.getByRole('button', {name: 'Settings'});
	}

	get menu() {
		return this.page.getByRole('menu');
	}

	getUserMenuButton(name: string) {
		return this.page.getByRole('button', {name, exact: true});
	}

	getMenuText(text: string) {
		return this.menu.getByText(text, {exact: true});
	}

	get infoMenuButton() {
		return this.page.getByRole('button', {name: 'Info'});
	}

	get logoutMenuItem() {
		return this.page.getByRole('menuitem', {name: 'Log out'});
	}

	languageOption(language: string) {
		return this.page.getByRole('radio', {name: language});
	}

	async openUserMenu() {
		await this.userMenuButton.click();
	}

	async selectLanguage(language: string) {
		await this.languageOption(language).click();
	}
}

export {Header};
