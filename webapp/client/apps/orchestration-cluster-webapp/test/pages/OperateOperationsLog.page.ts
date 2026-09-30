/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class OperateOperationsLogPage extends BasePage {
	async goto(search = '') {
		return this.page.goto(`/operate/operations-log${search}`);
	}

	get table() {
		return this.page.getByTestId('operations-log-table');
	}

	get decisionNamesError() {
		return this.page.getByText("Couldn't load decision names. Audit logs are still available.");
	}

	get retryButton() {
		return this.page.getByRole('button', {name: 'Retry decision names'});
	}
}

export {OperateOperationsLogPage};
