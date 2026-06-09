/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {View} from './BasePage';

class Header extends View {
	private brandingName: string | undefined;

	constructor(page: Page, brandingName?: string) {
		super(page);
		this.brandingName = brandingName;
	}

	get branding() {
		if (this.brandingName === undefined) {
			throw new Error('No branding name configured for this page');
		}

		return this.page.getByRole('banner', {name: this.brandingName});
	}

	get userSidebarToggle() {
		return this.page.getByRole('button', {name: /open settings/i});
	}

	get infoSidebarToggle() {
		return this.page.getByRole('button', {name: /open info/i});
	}

	get logoutButton() {
		return this.page.getByRole('button', {name: /log out/i});
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

	get languageSelector() {
		return this.page.getByRole('combobox', {name: 'Language'});
	}

	async openUserSidebar() {
		await this.userSidebarToggle.click();
	}

	async openInfoSidebar() {
		await this.infoSidebarToggle.click();
	}

	async selectLanguage(language: string) {
		await this.languageSelector.click();
		await this.page.getByRole('option', {name: language}).click();
	}

	async logout() {
		await this.openUserSidebar();
		await this.logoutButton.click();
	}
}

export {Header};
