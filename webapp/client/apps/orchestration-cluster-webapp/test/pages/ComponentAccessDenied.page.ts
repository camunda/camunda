/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type Page} from '@playwright/test';
import {View} from './BasePage';
import {Header} from './Header';

class ComponentAccessDeniedPage extends View {
	readonly header: Header;

	constructor(page: Page) {
		super(page);
		this.header = new Header(page);
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'You don’t have access to this component'});
	}

	get description() {
		return this.page.getByText(/contact your cluster admin/i);
	}

	get documentationLink() {
		return this.page.getByRole('link', {name: 'Learn more about roles and permissions'});
	}
}

export {ComponentAccessDeniedPage};
