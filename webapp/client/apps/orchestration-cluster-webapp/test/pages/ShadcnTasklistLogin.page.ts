/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LoginPage} from './Login.page';

class ShadcnTasklistLoginPage extends LoginPage {
	override async goto(redirect?: string) {
		const search = redirect === undefined ? '' : `?${new URLSearchParams({redirect})}`;
		return this.page.goto(`/shadcn/tasklist/login${search}`);
	}

	async gotoTasklist(search = '') {
		return this.page.goto(`/shadcn/tasklist${search}`);
	}

	override get passwordInput() {
		return this.page.getByLabel(/password/i);
	}

	get genericErrorHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}

	get title() {
		return this.page.getByRole('heading', {name: 'Tasklist'});
	}

	get usernameError() {
		return this.page.getByRole('alert').filter({hasText: /username is required/i});
	}

	get passwordError() {
		return this.page.getByRole('alert').filter({hasText: /password is required/i});
	}
}

export {ShadcnTasklistLoginPage};
