/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class AdminIndexPage extends BasePage {
	async goto() {
		return this.page.goto('/admin');
	}

	async gotoSection(section: string) {
		return this.page.goto(`/admin/${section}`);
	}

	get branding() {
		return this.page.getByRole('link', {name: 'Camunda logo'});
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'Admin'});
	}

	navItem(name: string) {
		return this.page.getByRole('link', {name, exact: true});
	}

	sectionHeading(name: string) {
		return this.page.getByRole('heading', {name, exact: true});
	}
}

export {AdminIndexPage};
