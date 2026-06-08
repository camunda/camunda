/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {Header} from './Header';

class NotFoundPage {
	private readonly page: Page;
	readonly header: Header;

	constructor(page: Page) {
		this.page = page;
		this.header = new Header(page);
	}

	get heading() {
		return this.page.getByRole('heading', {name: '404 - Page not found'});
	}

	get description() {
		return this.page.getByText(/the requested URL.*could not be found/i);
	}

	get goToHomeButton() {
		return this.page.getByRole('link', {name: 'Go to home'});
	}
}

export {NotFoundPage};
