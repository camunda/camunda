/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from './BasePage';
import {Header} from './Header';

class TasklistProcessesPage extends BasePage {
	readonly header: Header;

	constructor(page: Page) {
		super(page);
		this.header = new Header(page);
	}

	async goto() {
		return this.page.goto('/tasklist/processes');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Tasklist Processes'});
	}

	get tasksNavItem() {
		return this.page.getByRole('link', {name: 'Tasks'});
	}

	get processesNavItem() {
		return this.page.getByRole('link', {name: 'Processes'});
	}
}

export {TasklistProcessesPage};
