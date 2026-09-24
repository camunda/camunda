/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class OperateDecisionInstancePage extends BasePage {
	async goto(decisionInstanceId: string) {
		return this.page.goto(`/operate/decisions/${decisionInstanceId}`);
	}

	get decisionPanel() {
		return this.page.getByRole('region', {name: 'decision panel'});
	}

	get loadingSpinner() {
		return this.decisionPanel.getByRole('img', {name: 'loading'});
	}

	get decisionTableLabel() {
		return this.decisionPanel.getByText('Invoice Amount');
	}

	get xmlForbiddenMessage() {
		return this.decisionPanel.getByText('Missing permissions to view the Definition');
	}

	get panelErrorMessage() {
		return this.decisionPanel.getByText("Couldn't fetch data");
	}

	get panelRetryButton() {
		return this.decisionPanel.getByRole('button', {name: 'Try again'});
	}

	get pageErrorHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}

	get pageErrorRetryButton() {
		return this.page.getByRole('button', {name: 'Try again'});
	}
}

export {OperateDecisionInstancePage};
