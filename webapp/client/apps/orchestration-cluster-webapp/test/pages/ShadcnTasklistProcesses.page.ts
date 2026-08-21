/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {BasePage} from './BasePage';
import {ShadcnHeader} from './ShadcnHeader';

class ShadcnTasklistProcessesPage extends BasePage {
	readonly header: ShadcnHeader;

	constructor(page: Page) {
		super(page);
		this.header = new ShadcnHeader(page);
	}

	async goto() {
		return this.page.goto('/shadcn/tasklist/processes');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Processes', exact: true});
	}

	get description() {
		return this.page.getByText('Browse and run processes published by your organization.');
	}

	get startProcessButtons() {
		return this.page.getByRole('button', {name: 'Start process'});
	}
}

export {ShadcnTasklistProcessesPage};
