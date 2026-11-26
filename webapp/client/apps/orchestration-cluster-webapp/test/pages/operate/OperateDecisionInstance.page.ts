/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator} from '@playwright/test';
import {BasePage} from '#/pages/BasePage';

class OperateDecisionInstancePage extends BasePage {
	async goto(decisionInstanceId: string) {
		return this.page.goto(`/operate/decisions/${decisionInstanceId}`);
	}

	get instanceHeader() {
		return this.page.getByRole('group', {name: 'Instance header', exact: true});
	}

	get variablesPanel() {
		return this.page.getByRole('tabpanel');
	}

	get evaluatedStateOverlay() {
		return this.drdPanel.getByRole('img', {name: /: Evaluated$/}).first();
	}

	get decisionPanel() {
		return this.page.getByRole('region', {name: 'decision panel'});
	}

	get drdPanel() {
		return this.page.getByRole('region', {name: 'DRD panel'});
	}

	get drdDiagram() {
		return this.page.getByTestId('drd');
	}

	get drdResizeHandle() {
		return this.page.getByRole('separator', {name: 'Resize DRD panel'});
	}

	get maximizeDrd() {
		return this.page.getByRole('button', {name: 'Maximize DRD panel'});
	}

	get minimizeDrd() {
		return this.page.getByRole('button', {name: 'Minimize DRD panel'});
	}

	get closeDrd() {
		return this.page.getByRole('button', {name: 'Close DRD panel'});
	}

	async boundsOf(locator: Locator) {
		const bounds = await locator.boundingBox();
		if (bounds === null) {
			throw new Error(`Missing bounds for ${locator}`);
		}
		return bounds;
	}

	get decisionTableLabel() {
		return this.decisionPanel.getByText('Invoice Amount');
	}
}

export {OperateDecisionInstancePage};
