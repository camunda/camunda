/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
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
		await expect
			.element(screen.getByRole('rowgroup').filter({hasText: 'When'}).getByRole('cell', {name: '#'}))
			.toBeVisible();
	});

	it('should highlight only the given rule rows', async () => {
		const screen = await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
				highlightableRules={[1]}
			/>,
		);

		const dayToDayRule = screen.getByRole('row').filter({hasText: '"day-to-day expense"'});
		const managerApprovalRule = screen.getByRole('row').filter({hasText: '"manager approval"'});
		const boardApprovalRule = screen.getByRole('row').filter({hasText: '"board approval"'});

		await expect.element(dayToDayRule).toHaveAttribute('aria-current', 'true');
		await expect.element(managerApprovalRule).not.toHaveAttribute('aria-current');
		await expect.element(boardApprovalRule).not.toHaveAttribute('aria-current');
	});

	it('should update highlighted rows when the rules change', async () => {
		const screen = await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
				highlightableRules={[1]}
			/>,
		);
		const dayToDayRule = screen.getByRole('row').filter({hasText: '"day-to-day expense"'});
		await expect.element(dayToDayRule).toHaveAttribute('aria-current', 'true');

		await screen.rerender(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
				highlightableRules={[]}
			/>,
		);

		await expect.element(dayToDayRule).not.toHaveAttribute('aria-current');
	});

	it('should report the imported definitions', async () => {
		const onDefinitionsChange = vi.fn();

		await render(
			<DecisionViewer
				xml={DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE}
				decisionViewId="invoiceClassification"
				onDefinitionsChange={onDefinitionsChange}
			/>,
		);

		await expect
			.poll(() => onDefinitionsChange)
			.toHaveBeenCalledWith(expect.objectContaining({id: expect.any(String)}));
	});

	it('should render nothing in the canvas when there is no xml', async () => {
		const screen = await render(<DecisionViewer xml={null} decisionViewId={null} />);

		await expect.element(screen.getByTestId('decision-viewer')).toBeVisible();
		await expect.element(screen.getByText('Invoice Amount')).not.toBeInTheDocument();
	});

	it('should show the latest view when the selection changes while a render is pending', async () => {
		// given
		const xml = DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE;
		const screen = await render(<DecisionViewer xml={xml} decisionViewId="invoiceClassification" />);

		// when
		await screen.rerender(<DecisionViewer xml={xml} decisionViewId="calc-key-figures" />);

		// then
		await expect.element(screen.getByText(/avg_score/)).toBeVisible();
	});
});
