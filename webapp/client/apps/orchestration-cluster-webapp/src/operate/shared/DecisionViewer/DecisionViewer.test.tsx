/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect, vi} from 'vitest';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {DecisionViewer} from './index';

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

	it('should call onDefinitionsChange with the parsed decision definition', async () => {
		const onDefinitionsChange = vi.fn();

		await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
				onDefinitionsChange={onDefinitionsChange}
			/>,
		);

		await expect.poll(() => onDefinitionsChange.mock.calls.length).toBeGreaterThan(0);
		expect(onDefinitionsChange).toHaveBeenCalledWith(
			expect.objectContaining({id: 'invoiceBusinessDecisions', name: 'Invoice Business Decisions'}),
		);
	});

	it('should generate a highlight rule for the configured rule row', async () => {
		const screen = await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
				highlightableRules={[1]}
			/>,
		);

		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();

		const styleContent = Array.from(document.querySelectorAll('style'))
			.map((style) => style.textContent)
			.join('\n');

		expect(styleContent).toContain('tr:nth-child(1)');
	});

	it('should provide an accessible header label for the decision table index column', async () => {
		const screen = await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
			/>,
		);

		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
		await expect.element(screen.getByText('#', {exact: true}).first()).toBeVisible();
	});
});
