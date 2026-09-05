/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {ShadcnNotifications} from './ShadcnNotifications';
import {View} from './BasePage';

type LanguageOption = 'English' | 'Français' | 'Deutsch' | 'Español';

class ShadcnHeader extends View {
	readonly notifications: ShadcnNotifications;

	constructor(page: Page) {
		super(page);
		this.notifications = new ShadcnNotifications(page);
	}

	get branding() {
		return this.page.getByRole('link', {name: 'Camunda logo'});
	}

	get productBreadcrumb() {
		return this.page.getByRole('navigation', {name: 'Camunda context'}).getByRole('link');
	}

	get userSidebarToggle() {
		return this.page.getByRole('button', {name: 'Settings'});
	}

	get infoSidebarToggle() {
		return this.page.getByRole('button', {name: 'Info'});
	}

	get logoutButton() {
		return this.page.getByRole('menuitem', {name: /log out/i});
	}

	get documentationLink() {
		return this.page.getByRole('menuitem', {name: 'Documentation'});
	}

	get camundaAcademyLink() {
		return this.page.getByRole('menuitem', {name: 'Camunda Academy'});
	}

	get communityForumLink() {
		return this.page.getByRole('menuitem', {name: 'Community Forum'});
	}

	get feedbackAndSupportLink() {
		return this.page.getByRole('menuitem', {name: 'Feedback and Support'});
	}

	get languageSelector() {
		return this.page.getByRole('radiogroup', {name: 'Language'});
	}

	async openUserSidebar() {
		await this.userSidebarToggle.click();
	}

	async closeUserSidebar() {
		await this.page.keyboard.press('Escape');
	}

	async openInfoSidebar() {
		await this.infoSidebarToggle.click();
	}

	getLanguageOption(language: LanguageOption) {
		return this.page.getByRole('radio', {name: language});
	}

	async selectLanguage(language: LanguageOption) {
		await this.getLanguageOption(language).click();
	}

	async selectTheme(theme: string) {
		await this.page.getByRole('radio', {name: theme}).click();
	}

	get tasksNavItem() {
		return this.page.getByRole('link', {name: 'Tasks', exact: true});
	}

	get processesNavItem() {
		return this.page.getByRole('link', {name: 'Processes', exact: true});
	}

	async logout() {
		await this.openUserSidebar();
		await this.logoutButton.click();
	}
}

export {ShadcnHeader};
