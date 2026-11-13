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
	private brandingName: string | undefined;
	readonly notifications: Notifications;

	constructor(page: Page, brandingName?: string) {
		super(page);
		this.brandingName = brandingName;
		this.notifications = new Notifications(page);
	}

	get branding() {
		if (this.brandingName === undefined) {
			throw new Error('No branding name configured for this page');
		}

		return this.page.getByRole('banner', {name: this.brandingName});
	}

	get userMenuButton() {
		return this.page.getByRole('button', {name: /^User menu/});
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

	get infoSidebarToggle() {
		return this.page.getByRole('button', {name: /open info/i});
	}

	get logoutMenuItem() {
		return this.page.getByRole('menuitem', {name: 'Log out'});
	}

	get documentationLink() {
		return this.page.getByRole('button', {name: 'Documentation'});
	}

	get camundaAcademyLink() {
		return this.page.getByRole('button', {name: 'Camunda Academy'});
	}

	get communityForumLink() {
		return this.page.getByRole('button', {name: 'Community Forum'});
	}

	get feedbackAndSupportLink() {
		return this.page.getByRole('button', {name: 'Feedback and Support'});
	}

	languageMenuItem(language: string) {
		return this.page.getByRole('menuitemradio', {name: language});
	}

	async openUserMenu() {
		await this.userMenuButton.click();
	}

	async openInfoSidebar() {
		await this.infoSidebarToggle.click();
	}

	async selectLanguage(language: string) {
		await this.languageMenuItem(language).click();
	}
}

export {Header};
