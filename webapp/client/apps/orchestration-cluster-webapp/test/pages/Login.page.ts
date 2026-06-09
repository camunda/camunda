/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from './BasePage';

class LoginPage extends BasePage {
	constructor(page: Page) {
		super(page);
	}

	async goto() {
		return this.page.goto('/login');
	}

	get submitButton() {
		return this.page.getByRole('button', {name: /login/i});
	}

	get usernameInput() {
		return this.page.getByLabel(/username/i);
	}

	get passwordInput() {
		return this.page.getByLabel(/^password$/i);
	}

	get errorMessage() {
		return this.page.getByRole('alert').filter({hasText: /.+/});
	}

	get loadingButton() {
		return this.page.getByRole('button', {name: /logging in/i});
	}

	async fillCredentials(username: string, password: string) {
		await this.usernameInput.fill(username);
		await this.passwordInput.fill(password);
	}
}

export {LoginPage};
