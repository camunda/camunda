/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {DecisionViewer} from './DecisionViewer';

describe('<DecisionViewer />', () => {
	it('should render a decision table for a decision table view id', async () => {
		const screen = await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
			/>,
		);

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
	});

	it('should render a literal expression for a literal expression view id', async () => {
		const screen = await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="calc-key-figures"
			/>,
		);

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
		await expect.element(screen.getByText(/avg_score/)).toBeVisible();
	});

	it('should label the blank rule index column header', async () => {
		const screen = await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
			/>,
		);

		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
		const indexHeader = screen.getByText('#', {exact: true}).first();
		await expect.element(indexHeader).toBeVisible();
		expect(indexHeader.element().tagName).toBe('TH');
	});

	it('should render nothing in the canvas when there is no xml', async () => {
		const screen = await render(<DecisionViewer xml={null} decisionViewId={null} />);

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
		await expect.element(screen.getByText('Invoice Amount')).not.toBeInTheDocument();
	});
});
