/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class ShadcnTasklistProcessesPage extends BasePage {
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

	get tasksNavItem() {
		return this.page.getByRole('link', {name: 'Tasks', exact: true});
	}

	get processesNavItem() {
		return this.page.getByRole('link', {name: 'Processes', exact: true});
	}
}

export {ShadcnTasklistProcessesPage};
