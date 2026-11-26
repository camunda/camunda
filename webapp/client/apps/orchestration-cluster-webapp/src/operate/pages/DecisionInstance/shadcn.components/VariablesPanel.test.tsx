/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, beforeAll} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetDecisionInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {VariablesPanel} from './VariablesPanel';
import {preloadMonaco} from '#/vitest-modules/preload-monaco';

const TABLE_ID = '4294980768';
const ROUTE = '/operate/decisions/$decisionInstanceId';

function renderPanel() {
	return renderWithRouter(
		() => (
			<div style={{height: '600px'}}>
				<VariablesPanel decisionEvaluationInstanceKey={TABLE_ID} />
			</div>
		),
		{path: ROUTE, initialEntry: `/operate/decisions/${TABLE_ID}`},
	);
}

describe('<VariablesPanel />', () => {
	beforeAll(preloadMonaco, 120_000);

	afterEach(() => {
		localStorage.removeItem('operate.panelStates');
	});

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
								ruleId: 'rule-1',
								ruleIndex: 1,
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

		await expect.element(screen.getByRole('tab', {name: 'Inputs and outputs'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toBeVisible();
		await expect
			.element(screen.getByRole('tab', {name: 'Inputs and outputs'}))
			.toHaveAttribute('aria-selected', 'true');

		const inputs = screen.getByRole('region', {name: 'input variables'});
		const outputs = screen.getByRole('region', {name: 'output variables'});
		await expect.element(inputs.getByText('Customer age')).toBeVisible();
		await expect.element(inputs.getByText('42')).toBeVisible();
		await expect.element(outputs.getByText('Rule')).toBeVisible();
		await expect.element(outputs.getByText('3')).toBeVisible();
		await expect.element(outputs.getByText('Classification')).toBeVisible();
		await expect.element(outputs.getByText('"preferred"')).toBeVisible();
		await expect.element(outputs.getByText('1')).toBeVisible();
		await expect.element(outputs.getByText('Review')).toBeVisible();
		await expect.element(screen.getByTestId('results-json-viewer')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toHaveAttribute('aria-selected', 'true');
		await expect.element(screen.getByTestId('results-json-viewer')).toBeVisible();

		await userEvent.click(screen.getByRole('tab', {name: 'Inputs and outputs'}));
		await expect.element(inputs.getByText('Customer age')).toBeVisible();
		await expect.element(screen.getByTestId('results-json-viewer')).not.toBeVisible();
	});
	it('should render distinct output rows when rule identity and output IDs repeat', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						matchedRules: [
							{
								ruleId: 'rule-1',
								ruleIndex: 1,
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
								ruleId: 'rule-1',
								ruleIndex: 1,
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

		const screen = await renderPanel();
		const outputs = screen.getByRole('region', {name: 'output variables'});
		const rows = outputs.getByRole('row');
		await expect.element(rows.nth(1).getByText('First outcome')).toBeVisible();
		await expect.element(rows.nth(1).getByText('"first"')).toBeVisible();
		await expect.element(rows.nth(2).getByText('Second outcome')).toBeVisible();
		await expect.element(rows.nth(2).getByText('"second"')).toBeVisible();
		await expect.element(rows).toHaveLength(3);
	});

	it('should show both input and output skeletons during loading', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance()),
				delay: 'infinite',
			}),
		);

		const screen = await renderPanel();
		await expect.element(screen.getByTestId('inputs-skeleton')).toBeVisible();
		await expect.element(screen.getByTestId('outputs-skeleton')).toBeVisible();
		await expect.element(screen.getByTestId('results-json-viewer')).not.toBeInTheDocument();
	});

	it('should show the failed-evaluation empty state for missing inputs', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						state: 'FAILED',
						evaluatedInputs: [],
					}),
				),
			}),
		);

		const screen = await renderPanel();
		await expect.element(screen.getByText('No input available', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('No output available', {exact: true})).toBeVisible();

		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByText('No result available', {exact: true})).toBeVisible();
	});

	it('should show the failed-evaluation output empty state and keep existing inputs', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						state: 'FAILED',
						evaluatedInputs: [{inputId: 'failed-in', inputName: 'Age', inputValue: '42'}],
					}),
				),
			}),
		);

		const screen = await renderPanel();
		await expect.element(screen.getByText('Age')).toBeVisible();
		await expect.element(screen.getByText('No output available', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('No input available', {exact: true})).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByText('No result available', {exact: true})).toBeVisible();
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
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance()),
				delay: 'infinite',
			}),
		);

		const screen = await renderPanel();
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));

		await expect.element(screen.getByRole('status')).toHaveTextContent('Loading result');
		await expect.element(screen.getByTestId('results-json-viewer')).not.toBeInTheDocument();
	});

	it('should show numeric results without losing precision', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: TABLE_ID,
						result: '{"customerId":9007199254740993}',
					}),
				),
			}),
		);

		const screen = await renderPanel();
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));

		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/9007199254740993/);
	});
});
