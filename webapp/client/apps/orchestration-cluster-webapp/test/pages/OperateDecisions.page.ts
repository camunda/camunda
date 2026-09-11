/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class OperateDecisionsPage extends BasePage {
	async goto(search = '') {
		return this.page.goto(`/operate/decisions${search}`);
	}

	get deleteDefinitionButton() {
		return this.page.getByRole('button', {name: /Delete Decision Definition/});
	}

	get confirmDeleteButton() {
		return this.page.getByRole('button', {name: 'Delete', exact: true});
	}

	get confirmation() {
		return this.page.getByText('Yes, I confirm I want to delete this DRD and all related instances.');
	}

	async deleteDefinition() {
		await this.deleteDefinitionButton.click();
		await this.confirmation.click();
		await this.confirmDeleteButton.click();
	}

	async preloadDashboard() {
		await this.page.getByRole('link', {name: 'Dashboard', exact: true}).hover();
	}
}

export {OperateDecisionsPage};
