/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {useParams} from '@tanstack/react-router';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetDecisionInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
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

	it('should show both input and output skeletons during loading', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance()),
				delay: 600,
			}),
		);

		const screen = await renderPanel();

		await expect.element(screen.getByTestId('inputs-skeleton')).toBeVisible();
		await expect.element(screen.getByTestId('outputs-skeleton')).toBeVisible();
		await expect.element(screen.getByTestId('inputs-skeleton')).not.toBeInTheDocument();
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
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance()),
				delay: 600,
			}),
		);

		const screen = await renderPanel();

		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByTestId('result-loading-spinner')).toBeVisible();
		await expect.element(screen.getByTestId('results-json-viewer').getByRole('textbox', {name: 'Value'})).toBeVisible();
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
