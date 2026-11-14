/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';
import {TasklistHeader} from './TasklistHeader';

class TasklistCasesPage extends BasePage {
	readonly header: TasklistHeader;

	constructor(page: Page) {
		super(page);
		this.header = new TasklistHeader(page);
	}

	async goto(search = '') {
		return this.page.goto(`/tasklist/cases${search}`);
	}

	get noOpenCasesHeading() {
		return this.page.getByRole('heading', {name: 'No open cases'});
	}

	get noMatchingCasesHeading() {
		return this.page.getByRole('heading', {name: 'No matching cases'});
	}
}

export {TasklistCasesPage};
