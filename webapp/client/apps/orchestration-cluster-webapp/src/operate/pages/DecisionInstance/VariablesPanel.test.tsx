/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {useParams} from '@tanstack/react-router';
import {userEvent} from 'vitest/browser';
import {HttpResponse, http} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetDecisionInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {VariablesPanel} from './VariablesPanel';

const TABLE_ID = '4294980768';
const LITERAL_ID = '4294980769';
const NEXT_TABLE_ID = '4294980770';
const ROUTE = '/operate/decisions/$decisionInstanceId';

function PanelOnRoute() {
	const {decisionInstanceId} = useParams({strict: false});
	return (
		<div style={{height: '600px'}}>
			<VariablesPanel decisionEvaluationInstanceKey={decisionInstanceId ?? ''} />
		</div>
	);
}

function renderPanel() {
	return renderWithRouter(PanelOnRoute, {
		path: ROUTE,
		initialEntry: `/operate/decisions/${TABLE_ID}`,
	});
}

describe('<VariablesPanel />', () => {
	it.for([
		{initialWidth: 0, change: 'appears', side: 'input'},
		{initialWidth: 0, change: 'appears', side: 'output'},
		{initialWidth: 600, change: 'grows', side: 'input'},
		{initialWidth: 600, change: 'grows', side: 'output'},
	] as const)(
		'should keep the $side panel at least one third wide when the container $change',
		async ({initialWidth, side}, {worker}) => {
			worker.use(
				mockGetDecisionInstanceEndpoint({
					successResponse: HttpResponse.json(createDecisionInstance({decisionEvaluationInstanceKey: TABLE_ID})),
				}),
			);

			const previousPanelStates = localStorage.getItem('operate.panelStates');
			try {
				storeStateLocally('operate.panelStates', {
					...(getStateLocally('operate.panelStates') ?? {}),
					'decision-instance-horizontal-panel': [50, 50],
				});
				const screen = await renderWithRouter(
					() => (
						<div data-testid="resizable-panel-container" style={{width: initialWidth}}>
							<PanelOnRoute />
						</div>
					),
					{path: ROUTE, initialEntry: `/operate/decisions/${TABLE_ID}`},
				);
				const container = screen.getByTestId('resizable-panel-container');
				const panel = screen.getByRole('region', {name: `${side} variables`});
				container.element().style.width = '900px';

				await expect.poll(() => panel.element().getBoundingClientRect().width).toBeGreaterThan(400);
				const dragger = screen
					.getByTestId('decision-instance-variables-panel')
					.element()
					.querySelector<HTMLElement>('.custom-dragger-Horizontal');
				if (dragger === null) {
					throw new Error('Expected a horizontal panel dragger');
				}
				await userEvent.dragAndDrop(dragger, panel);

				await expect.poll(() => panel.element().getBoundingClientRect().width).toBeLessThan(360);
				expect(panel.element().getBoundingClientRect().width).toBeGreaterThanOrEqual(
					container.element().getBoundingClientRect().width / 3 - 1,
				);
			} finally {
				if (previousPanelStates === null) {
					localStorage.removeItem('operate.panelStates');
				} else {
					localStorage.setItem('operate.panelStates', previousPanelStates);
				}
			}
		},
	);

	it('should display evaluated inputs and matched-rule outputs in separate structured tables', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						evaluatedInputs: [{inputId: 'in-1', inputName: 'Customer age', inputValue: '42'}],
						matchedRules: [
							{
								ruleId: 'rule-1',
								ruleIndex: 3,
								evaluatedOutputs: [
									{
										outputId: 'out-1',
										outputName: 'Classification',
										outputValue: '"preferred"',
										ruleId: 'rule-1',
										ruleIndex: 3,
									},
								],
							},
							{
								ruleId: null,
								ruleIndex: null,
								evaluatedOutputs: [
									{
										outputId: 'out-2',
										outputName: 'Review',
										outputValue: 'false',
										ruleId: null,
										ruleIndex: null,
									},
								],
							},
						],
					}),
				),
			}),
		);

		const screen = await renderPanel();

		await expect.element(screen.getByRole('tab', {name: 'Inputs and Outputs'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toBeVisible();
		await expect
			.element(screen.getByRole('tab', {name: 'Inputs and Outputs'}))
			.toHaveAttribute('aria-selected', 'true');
		const inputs = screen.getByRole('region', {name: 'input variables'});
		const outputs = screen.getByRole('region', {name: 'output variables'});
		await expect.element(inputs.getByText('Customer age')).toBeVisible();
		await expect.element(inputs.getByText('42')).toBeVisible();
		await expect.element(outputs.getByText('Rule')).toBeVisible();
		await expect.element(outputs.getByText('3', {exact: true})).toBeVisible();
		await expect.element(outputs.getByText('Classification')).toBeVisible();
		await expect.element(outputs.getByText('"preferred"')).toBeVisible();
		await expect.element(outputs.getByText('--')).toBeVisible();
		await expect.element(outputs.getByText('Review')).toBeVisible();
		await expect.element(screen.getByTestId('results-json-viewer')).not.toBeInTheDocument();
		expect(screen.getByRole('tabpanel').element().getBoundingClientRect().bottom).toBeLessThanOrEqual(
			screen.getByTestId('decision-instance-variables-panel').element().getBoundingClientRect().bottom + 1,
		);

		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toHaveAttribute('aria-selected', 'true');
		await expect.element(screen.getByTestId('results-json-viewer').getByRole('textbox', {name: 'Value'})).toBeVisible();

		await userEvent.click(screen.getByRole('tab', {name: 'Inputs and Outputs'}));
		await expect.element(inputs.getByText('Customer age')).toBeVisible();
	});

	it('should refresh input and output rows when navigating between decision tables', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						evaluatedInputs: [{inputId: 'input', inputName: 'First input', inputValue: '1'}],
						matchedRules: [
							{
								ruleId: null,
								ruleIndex: null,
								evaluatedOutputs: [
									{outputId: 'output', outputName: 'First output', outputValue: '"one"', ruleId: null, ruleIndex: null},
								],
							},
						],
					}),
				),
			}),
		);

		const screen = await renderPanel();
		const inputs = screen.getByRole('region', {name: 'input variables'});
		const outputs = screen.getByRole('region', {name: 'output variables'});
		await expect.element(inputs.getByText('First input')).toBeVisible();
		await expect.element(outputs.getByText('First output')).toBeVisible();

		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: NEXT_TABLE_ID,
						evaluatedInputs: [{inputId: 'input', inputName: 'Next input', inputValue: '2'}],
						matchedRules: [
							{
								ruleId: null,
								ruleIndex: null,
								evaluatedOutputs: [
									{outputId: 'output', outputName: 'Next output', outputValue: '"two"', ruleId: null, ruleIndex: null},
								],
							},
						],
					}),
				),
			}),
		);
		await screen.router.navigate({to: ROUTE, params: {decisionInstanceId: NEXT_TABLE_ID}});

		await expect.element(inputs.getByText('Next input')).toBeVisible();
		await expect.element(outputs.getByText('Next output')).toBeVisible();
		await expect.element(inputs.getByText('First input')).not.toBeInTheDocument();
		await expect.element(outputs.getByText('First output')).not.toBeInTheDocument();
	});

	it('should render distinct output rows when rule identity and output IDs repeat', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						matchedRules: [
							{
								ruleId: null,
								ruleIndex: null,
								evaluatedOutputs: [
									{
										outputId: 'shared-output',
										outputName: 'First outcome',
										outputValue: '"first"',
										ruleId: null,
										ruleIndex: null,
									},
								],
							},
							{
								ruleId: null,
								ruleIndex: null,
								evaluatedOutputs: [
									{
										outputId: 'shared-output',
										outputName: 'Second outcome',
										outputValue: '"second"',
										ruleId: null,
										ruleIndex: null,
									},
								],
							},
						],
					}),
				),
			}),
		);

		const consoleError = vi.spyOn(console, 'error');
		try {
			const screen = await renderPanel();
			const outputs = screen.getByRole('region', {name: 'output variables'});
			const rows = outputs.getByRole('row');
			await expect.element(rows.nth(1).getByText('First outcome')).toBeVisible();
			await expect.element(rows.nth(1).getByText('"first"')).toBeVisible();
			await expect.element(rows.nth(2).getByText('Second outcome')).toBeVisible();
			await expect.element(rows.nth(2).getByText('"second"')).toBeVisible();
			expect(
				consoleError.mock.calls.some(
					([message]) => typeof message === 'string' && message.includes('Encountered two children with the same key'),
				),
			).toBe(false);
		} finally {
			consoleError.mockRestore();
		}
	});

	it('should show both input and output skeletons during loading', async ({worker}) => {
		let release!: () => void;
		const pending = new Promise<void>((resolve) => {
			release = resolve;
		});
		worker.use(
			http.get(
				endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: ':decisionEvaluationInstanceKey'}),
				async () => {
					await pending;
					return HttpResponse.json(createDecisionInstance());
				},
			),
		);

		try {
			const screen = await renderPanel();
			await expect.element(screen.getByTestId('inputs-skeleton')).toBeVisible();
			await expect.element(screen.getByTestId('outputs-skeleton')).toBeVisible();
			await expect.element(screen.getByTestId('results-json-viewer')).not.toBeInTheDocument();
			release();
			await expect.element(screen.getByTestId('inputs-skeleton')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('region', {name: 'output variables'}).getByText('Rule')).toBeVisible();
		} finally {
			release();
		}
	});

	it.for([
		{name: 'no inputs', evaluatedInputs: []},
		{name: 'existing inputs', evaluatedInputs: [{inputId: 'failed-in', inputName: 'Age', inputValue: '42'}]},
	])('should show the failed-evaluation empty states with $name', async ({evaluatedInputs}, {worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						state: 'FAILED',
						evaluatedInputs,
					}),
				),
			}),
		);

		const screen = await renderPanel();

		await expect.element(screen.getByText('No output available because the evaluation failed')).toBeVisible();
		if (evaluatedInputs.length === 0) {
			await expect.element(screen.getByText('No input available because the evaluation failed')).toBeVisible();
		} else {
			await expect.element(screen.getByText('Age')).toBeVisible();
			await expect
				.element(screen.getByText('No input available because the evaluation failed'))
				.not.toBeInTheDocument();
		}

		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByText('No result available because the evaluation failed')).toBeVisible();
	});

	it('should display explicit errors in both panels when the query fails', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderPanel();

		await expect
			.element(screen.getByRole('region', {name: 'input variables'}).getByText("Couldn't fetch data"))
			.toBeVisible();
		await expect
			.element(screen.getByRole('region', {name: 'output variables'}).getByText("Couldn't fetch data"))
			.toBeVisible();
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	it('should show a result loading spinner when the result tab is selected while fetching', async ({worker}) => {
		let release!: () => void;
		const pending = new Promise<void>((resolve) => {
			release = resolve;
		});
		worker.use(
			http.get(
				endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: ':decisionEvaluationInstanceKey'}),
				async () => {
					await pending;
					return HttpResponse.json(createDecisionInstance());
				},
			),
		);

		try {
			const screen = await renderPanel();
			await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
			await expect.element(screen.getByTestId('result-loading-spinner')).toBeVisible();
			await expect.element(screen.getByTestId('results-json-viewer')).not.toBeInTheDocument();
			release();
			await expect.element(screen.getByTestId('result-loading-spinner')).not.toBeInTheDocument();
			await expect
				.element(screen.getByTestId('results-json-viewer').getByRole('textbox', {name: 'Value'}))
				.toBeVisible();
		} finally {
			release();
		}
	});

	it('should switch from a decision table to a literal expression and reset to the new result', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						result: '{"table":"matched"}',
					}),
				),
			}),
		);

		const screen = await renderPanel();
		await expect.element(screen.getByRole('tab', {name: 'Inputs and Outputs'})).toBeVisible();
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"table":"matched"/);

		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: LITERAL_ID,
						decisionDefinitionType: 'LITERAL_EXPRESSION',
						result: '{"literal":"selected"}',
					}),
				),
			}),
		);
		await screen.router.navigate({to: ROUTE, params: {decisionInstanceId: LITERAL_ID}});

		await expect.element(screen.getByRole('heading', {name: 'Result'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Inputs and Outputs'})).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"literal":"selected"/);
	});

	it('should preserve the selected result tab when navigating between decision tables', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						result: '{"table":"first"}',
					}),
				),
			}),
		);

		const screen = await renderPanel();
		await expect.element(screen.getByRole('tab', {name: 'Inputs and Outputs'})).toBeVisible();
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"table":"first"/);

		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: NEXT_TABLE_ID,
						result: '{"table":"next"}',
					}),
				),
			}),
		);
		await screen.router.navigate({to: ROUTE, params: {decisionInstanceId: NEXT_TABLE_ID}});

		await expect.element(screen.getByRole('tab', {name: 'Result'})).toHaveAttribute('aria-selected', 'true');
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"table":"next"/);
	});
});
