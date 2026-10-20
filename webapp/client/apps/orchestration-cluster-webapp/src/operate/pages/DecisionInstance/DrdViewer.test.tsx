/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {DrdViewer} from './DrdViewer';
import type {DrdData} from './drdData.queries';

const XML = DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE;
const DATA: DrdData = {
	invoiceClassification: {
		decisionDefinitionId: 'invoiceClassification',
		decisionEvaluationInstanceKey: 'instance-1',
		state: 'EVALUATED',
	},
	'calc-key-figures': {
		decisionDefinitionId: 'calc-key-figures',
		decisionEvaluationInstanceKey: 'instance-2',
		state: 'FAILED',
	},
};

function decisionShape(container: Element, id: string) {
	const shape = container.querySelector(`[data-element-id="${id}"]`);
	if (!(shape instanceof Element)) {
		throw new Error(`Missing DRD decision shape ${id}`);
	}
	return shape;
}

describe('<DrdViewer />', () => {
	it('should render the DRD with selectable and selected markers and state overlays', async () => {
		const onDefinitionsChange = vi.fn();
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
				onDefinitionsChange={onDefinitionsChange}
			/>,
		);

		await expect.element(screen.getByTestId('drd-viewer').getByText('invoiceClassification')).toBeVisible();
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		await expect.element(screen.getByTestId('state-overlay-FAILED')).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'invoiceClassification: Evaluated'})).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'calc-key-figures: Failed'})).toBeVisible();
		await expect.element(screen.getByTitle('Powered by bpmn.io')).toBeVisible();
		const container = screen.getByTestId('drd-viewer').element();
		expect(decisionShape(container, 'invoiceClassification')).toHaveClass('ope-selectable', 'ope-selected');
		expect(decisionShape(container, 'invoiceClassification').querySelector('.djs-outline')).not.toBeNull();
		expect(decisionShape(container, 'calc-key-figures')).toHaveClass('ope-selectable');
		expect(onDefinitionsChange).toHaveBeenCalledWith(
			expect.objectContaining({id: 'invoiceBusinessDecisions', name: 'Invoice Business Decisions'}),
		);
	});

	it('should navigate to the evaluation instance associated with a clicked decision', async () => {
		const onDecisionSelection = vi.fn();
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={onDecisionSelection}
			/>,
		);

		await expect.element(screen.getByTestId('state-overlay-FAILED')).toBeVisible();
		await userEvent.click(screen.getByTestId('drd-viewer').getByText('Calculate Credit History Key Figures'));
		expect(onDecisionSelection).toHaveBeenCalledWith('instance-2');
	});

	it('should let keyboard users focus and select evaluated decisions only', async () => {
		const onDecisionSelection = vi.fn();
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={{'calc-key-figures': DATA['calc-key-figures']!}}
				selectedDecisionEvaluationInstanceKey="instance-2"
				onDecisionSelection={onDecisionSelection}
			/>,
		);

		const decision = screen.getByRole('button', {name: 'Calculate Credit History Key Figures'});
		await expect.element(decision).toBeVisible();
		await expect.element(decision).toHaveAttribute('tabindex', '0');
		await expect.element(decision).toHaveAttribute('aria-current', 'true');
		expect(decisionShape(screen.getByTestId('drd-viewer').element(), 'invoiceClassification')).not.toHaveAttribute(
			'tabindex',
		);
		decision.element().focus();
		await expect.element(decision).toHaveFocus();
		await userEvent.keyboard('{Enter}');
		await userEvent.keyboard(' ');
		expect(onDecisionSelection).toHaveBeenCalledTimes(2);
		expect(onDecisionSelection).toHaveBeenCalledWith('instance-2');
	});

	it('should replace keyboard targets when the evaluation data changes', async () => {
		const onDecisionSelection = vi.fn();
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={onDecisionSelection}
			/>,
		);
		const firstDecision = screen.getByRole('button', {name: 'invoiceClassification'});
		await expect.element(firstDecision).toBeVisible();

		await screen.rerender(
			<DrdViewer
				xml={XML}
				data={{'calc-key-figures': DATA['calc-key-figures']!}}
				selectedDecisionEvaluationInstanceKey="instance-2"
				onDecisionSelection={onDecisionSelection}
			/>,
		);

		await expect.element(firstDecision).not.toBeInTheDocument();
		await expect
			.element(screen.getByRole('button', {name: 'Calculate Credit History Key Figures'}))
			.toHaveAttribute('aria-current', 'true');
		expect(decisionShape(screen.getByTestId('drd-viewer').element(), 'invoiceClassification')).not.toHaveAttribute(
			'tabindex',
		);
	});

	it('should retain the selected decision for assistive technology when other evaluations change', async () => {
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
			/>,
		);
		const decision = screen.getByRole('button', {name: 'invoiceClassification'});
		await expect.element(decision).toHaveAttribute('aria-current', 'true');

		await screen.rerender(
			<DrdViewer
				xml={XML}
				data={{invoiceClassification: DATA.invoiceClassification!}}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
			/>,
		);

		await expect.element(decision).toHaveAttribute('aria-current', 'true');
		await expect.element(decision).toHaveAttribute('tabindex', '0');
	});

	it('should render the DMN drill-down icon for a decision table', async () => {
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
			/>,
		);

		const drillDown = screen.getByTitle('Open decision table');
		await expect.element(drillDown).toBeVisible();
		const icon = window.getComputedStyle(drillDown.element(), '::before');
		expect(icon.fontFamily).toContain('dmn');
		expect(icon.content).not.toBe('none');
		await document.fonts.load('16px dmn');
		expect(document.fonts.check('16px dmn')).toBe(true);
	});

	it('should only notify once after replacing the DRD definition', async () => {
		const onDecisionSelection = vi.fn();
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={onDecisionSelection}
			/>,
		);
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();

		await screen.rerender(
			<DrdViewer
				xml={XML.replace('Invoice Business Decisions', 'Updated Invoice Decisions')}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={onDecisionSelection}
			/>,
		);
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		await userEvent.click(screen.getByTestId('drd-viewer').getByText('invoiceClassification'));
		expect(onDecisionSelection).toHaveBeenCalledExactlyOnceWith('instance-1');
		const decision = screen.getByRole('button', {name: 'invoiceClassification'});
		decision.element().focus();
		await userEvent.keyboard('{Enter}');
		expect(onDecisionSelection).toHaveBeenCalledTimes(2);
		expect(onDecisionSelection).toHaveBeenLastCalledWith('instance-1');
	});

	it('should refresh overlays and selection when the evaluation data changes without reimporting XML', async () => {
		const onDecisionSelection = vi.fn();
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={onDecisionSelection}
			/>,
		);
		await expect.element(screen.getByTestId('state-overlay-FAILED')).toBeVisible();

		const nextData: DrdData = {
			'calc-key-figures': {
				...DATA['calc-key-figures']!,
				decisionEvaluationInstanceKey: 'instance-3',
				state: 'EVALUATED',
			},
		};
		await screen.rerender(
			<DrdViewer
				xml={XML}
				data={nextData}
				selectedDecisionEvaluationInstanceKey="instance-3"
				onDecisionSelection={onDecisionSelection}
			/>,
		);

		await expect.element(screen.getByTestId('state-overlay-FAILED')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'calc-key-figures: Evaluated'})).toBeVisible();
		const container = screen.getByTestId('drd-viewer').element();
		expect(decisionShape(container, 'invoiceClassification')).not.toHaveClass('ope-selectable', 'ope-selected');
		expect(decisionShape(container, 'calc-key-figures')).toHaveClass('ope-selectable', 'ope-selected');
		await userEvent.click(screen.getByTestId('drd-viewer').getByText('Calculate Credit History Key Figures'));
		expect(onDecisionSelection).toHaveBeenCalledWith('instance-3');
	});

	it('should keep markers and overlays when the same decisions arrive in another order', async () => {
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
			/>,
		);
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		const overlay = screen.getByTestId('state-overlay-EVALUATED').element();

		await screen.rerender(
			<DrdViewer
				xml={XML}
				data={{'calc-key-figures': DATA['calc-key-figures']!, invoiceClassification: DATA.invoiceClassification!}}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
			/>,
		);

		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		expect(screen.getByTestId('state-overlay-EVALUATED').element()).toBe(overlay);
	});

	it('should move the selected marker when only the selected evaluation changes', async () => {
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
			/>,
		);
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();

		await screen.rerender(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-2"
				onDecisionSelection={() => {}}
			/>,
		);

		await expect
			.poll(() =>
				decisionShape(screen.getByTestId('drd-viewer').element(), 'calc-key-figures').classList.contains(
					'ope-selected',
				),
			)
			.toBe(true);
		expect(decisionShape(screen.getByTestId('drd-viewer').element(), 'invoiceClassification')).not.toHaveClass(
			'ope-selected',
		);
	});

	it('should select the current definition even when its evaluation was overwritten by a duplicate', async () => {
		const screen = await render(
			<DrdViewer
				xml={XML}
				data={{
					...DATA,
					invoiceClassification: {...DATA.invoiceClassification!, decisionEvaluationInstanceKey: 'latest-instance'},
				}}
				selectedDecisionEvaluationInstanceKey="earlier-instance"
				selectedDecisionDefinitionId="invoiceClassification"
				onDecisionSelection={() => {}}
			/>,
		);

		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		expect(decisionShape(screen.getByTestId('drd-viewer').element(), 'invoiceClassification')).toHaveClass(
			'ope-selected',
		);
	});

	it('should skip markers and overlays for evaluated decisions absent from the DRD diagram', async () => {
		const xmlWithOneShape = XML.replace(/<dmndi:DMNShape id="DMNShape_2"[\s\S]*?<\/dmndi:DMNShape>/, '');
		const screen = await render(
			<DrdViewer
				xml={xmlWithOneShape}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
			/>,
		);

		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
		await expect.element(screen.getByTestId('state-overlay-FAILED')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('drd-viewer').getByText('invoiceClassification')).toBeVisible();
	});

	it('should report an import error and recover when the XML is corrected', async () => {
		const onError = vi.fn();
		const screen = await render(
			<DrdViewer
				xml="<invalid"
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
				onError={onError}
			/>,
		);

		await expect.poll(() => onError.mock.calls.length).toBe(1);
		await screen.rerender(
			<DrdViewer
				xml={XML}
				data={DATA}
				selectedDecisionEvaluationInstanceKey="instance-1"
				onDecisionSelection={() => {}}
				onError={onError}
			/>,
		);
		await expect.element(screen.getByTestId('state-overlay-EVALUATED')).toBeVisible();
	});
});
