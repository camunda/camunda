/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from '#/pages/BasePage';

class AdminSetupPage extends BasePage {
	async goto() {
		return this.page.goto('/admin/setup');
	}

	get title() {
		return this.page.getByRole('heading', {name: 'Create admin user'});
	}

	get usernameInput() {
		return this.page.getByLabel(/^Username/);
	}

	get passwordInput() {
		return this.page.getByLabel(/^Password/);
	}

	get nameInput() {
		return this.page.getByLabel(/^Full name/);
	}

	get confirmPasswordInput() {
		return this.page.getByLabel(/^Confirm password/);
	}

	get submitButton() {
		return this.page.getByRole('button', {name: 'Create user'});
	}

	get passwordMismatchError() {
		return this.page.getByRole('alert').filter({hasText: 'Passwords do not match'});
	}

	errorMessage(text: string | RegExp) {
		return this.page.getByRole('alert').filter({hasText: text});
	}

	async fillRequiredFields(username: string, password: string) {
		await this.usernameInput.fill(username);
		await this.passwordInput.fill(password);
		await this.confirmPasswordInput.fill(password);
	}
}

export {AdminSetupPage};
