/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {useParams} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCurrentUserEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	DMN_XML,
	DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE,
} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {storeStateLocally} from '#/shared/browser-storage/local-storage';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {DecisionInstance, DecisionInstanceShell} from './DecisionInstance';

const DECISION_INSTANCE_ID = '4294980768';
const DECISION_INSTANCE = createDecisionInstance({
	decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
	decisionDefinitionId: 'invoiceClassification',
});
const PATH = '/operate/decisions/$decisionInstanceId';
const INITIAL_ENTRY = `/operate/decisions/${DECISION_INSTANCE_ID}`;

function mockPage(worker: {use: (...handlers: ReturnType<typeof mockGetDecisionInstanceEndpoint>[]) => void}) {
	worker.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}),
		mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
		mockQueryDecisionInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE]})),
		}),
	);
}

const EVALUATION_KEY = DECISION_INSTANCE.decisionEvaluationKey;
const DRD_SORT_SCHEMA = z.tuple([
	z.object({field: z.literal('decisionEvaluationInstanceKey'), order: z.literal('ASC')}),
]);
const FIRST_DRD_PAGE_SCHEMA = z.object({
	filter: z.object({decisionEvaluationKey: z.literal(EVALUATION_KEY)}),
	sort: DRD_SORT_SCHEMA,
	page: z.object({after: z.undefined().optional(), limit: z.literal(1000)}),
});

function nextDrdPageSchema(after: string) {
	return z.object({
		filter: z.object({decisionEvaluationKey: z.literal(EVALUATION_KEY)}),
		sort: DRD_SORT_SCHEMA,
		page: z.object({after: z.literal(after), limit: z.literal(1000)}),
	});
}

function mockDrdPage(schema: z.ZodType, successResponse: Response) {
	return mockQueryDecisionInstancesEndpoint({
		schema,
		successResponse,
		failureResponse: new HttpResponse(null, {status: 400}),
		once: true,
	});
}

function mockPageWithTwoDecisions(worker: {
	use: (...handlers: ReturnType<typeof mockGetDecisionInstanceEndpoint>[]) => void;
}) {
	worker.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(DECISION_INSTANCE)}),
		mockGetDecisionDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
		}),
	);
}

function createCalcKeyFigures(decisionEvaluationInstanceKey: string, state: 'EVALUATED' | 'FAILED') {
	return createDecisionInstance({
		decisionEvaluationKey: EVALUATION_KEY,
		decisionDefinitionId: 'calc-key-figures',
		decisionEvaluationInstanceKey,
		state,
	});
}

function mockDrdWithCalcKeyFigures() {
	return mockQueryDecisionInstancesEndpoint({
		successResponse: HttpResponse.json(
			createQueryDecisionInstancesResponse({items: [DECISION_INSTANCE, createCalcKeyFigures('calc-1', 'EVALUATED')]}),
		),
	});
}

const DecisionInstanceOnRoute = () => {
	const {decisionInstanceId} = useParams({strict: false}) as {decisionInstanceId: string};
	return (
		<div style={{height: '100vh'}}>
			<DecisionInstance decisionInstanceId={decisionInstanceId} />
		</div>
	);
};

function renderPage() {
	return renderWithRouter(
		() => (
			<div style={{height: '100vh'}}>
				<DecisionInstance decisionInstanceId={DECISION_INSTANCE_ID} />
			</div>
		),
		{path: PATH, initialEntry: INITIAL_ENTRY},
	);
}

describe('<DecisionInstance /> (design system)', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		localStorage.removeItem('operate.panelStates');
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
		localStorage.removeItem('operate.panelStates');
	});

	it('should announce a failed evaluation with a next step when no failure message exists', async ({worker}) => {
		// given
		const failed = createDecisionInstance({
			decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
			decisionDefinitionId: 'invoiceClassification',
			state: 'FAILED',
			evaluationFailure: undefined,
		});
		mockPage(worker);
		worker.use(mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(failed)}));

		// when
		const screen = await renderPage();

		// then
		await expect
			.element(screen.getByRole('alert'))
			.toHaveTextContent(
				'Evaluation failed for an unknown reason. Check the input values and the decision definition.',
			);
	});

	it('should show the loading states in the pending route shell', async () => {
		const screen = await renderWithRouter(
			() => (
				<div style={{height: '100vh'}}>
					<DecisionInstanceShell isPending header={<div>Loading decision instance</div>} />
				</div>
			),
			{path: PATH, initialEntry: INITIAL_ENTRY},
		);

		await expect.element(screen.getByRole('heading', {name: 'Operate decision instance'})).toBeInTheDocument();
		await expect.element(screen.getByTestId('diagram-spinner')).toBeVisible();
		await expect.element(screen.getByTestId('inputs-skeleton')).toBeVisible();
		await expect.element(screen.getByTestId('outputs-skeleton')).toBeVisible();
	});

	it('should render the header, evaluated decision panel and variables tabs', async ({worker}) => {
		mockPage(worker);

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Operate decision instance'})).toBeInTheDocument();
		await expect
			.element(screen.getByRole('region', {name: 'decision panel'}).getByText('Invoice Amount'))
			.toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Inputs and outputs'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toBeVisible();
		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
	});

	it('should keep the skip-to-content target when the decision instance fails to load', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json({}, {status: 500})}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
	});

	it('should keep the header and variables available when the decision XML cannot be imported', async ({worker}) => {
		mockPage(worker);
		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text('not a dmn document')}));

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Result'})).toBeVisible();
		await expect.element(screen.getByTestId('decision-panel').getByRole('button', {name: 'Try again'})).toBeVisible();
	});

	it('should refetch the decision XML when retrying after an import failure', async ({worker}) => {
		mockPage(worker);
		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text('not a dmn document')}));
		const screen = await renderPage();
		const retry = screen.getByTestId('decision-panel').getByRole('button', {name: 'Try again'});
		await expect.element(retry).toBeVisible();

		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}));
		await userEvent.click(retry);

		await expect
			.element(screen.getByRole('region', {name: 'decision panel'}).getByText('Invoice Amount'))
			.toBeVisible();
	});

	it('should switch between maximized and closed DRD layouts and restore focus', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await expect.element(screen.getByRole('region', {name: 'DRD panel'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Maximize DRD panel'}));

		await expect.element(screen.getByRole('button', {name: 'Minimize DRD panel'})).toHaveFocus();
		await expect.element(screen.getByTestId('instance-header')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('decision-panel')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Minimize DRD panel'}));
		await expect.element(screen.getByRole('button', {name: 'Maximize DRD panel'})).toHaveFocus();
		await expect.element(screen.getByTestId('instance-header')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Close DRD panel'}));
		await expect.element(screen.getByRole('button', {name: 'Open decision requirements diagram'})).toHaveFocus();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
	});

	it('should show the instance key in the maximized DRD and minimize it on Escape', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Maximize DRD panel'}));
		await expect.element(screen.getByTestId('drd').getByText(new RegExp(DECISION_INSTANCE_ID))).toBeVisible();

		await userEvent.keyboard('{Escape}');

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
	});

	it('should restore the closed DRD state from local storage', async ({worker}) => {
		mockPage(worker);
		storeStateLocally('operate.panelStates', {drdPanelState: 'closed'});

		const screen = await renderPage();

		await expect.element(screen.getByTestId('instance-header')).toBeVisible();
		await expect.element(screen.getByTestId('drd')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Open decision requirements diagram'})).toBeVisible();
	});

	it('should show the latest state of every decision across all pages of the evaluation', async ({worker}) => {
		mockPageWithTwoDecisions(worker);
		worker.use(
			mockDrdPage(
				FIRST_DRD_PAGE_SCHEMA,
				HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [DECISION_INSTANCE],
						page: {totalItems: 3, endCursor: 'page-1'},
					}),
				),
			),
			mockDrdPage(
				nextDrdPageSchema('page-1'),
				HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [createCalcKeyFigures('calc-1', 'EVALUATED')],
						page: {totalItems: 3, endCursor: 'page-2'},
					}),
				),
			),
			mockDrdPage(
				nextDrdPageSchema('page-2'),
				HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [createCalcKeyFigures('calc-2', 'FAILED')],
						page: {totalItems: 3},
					}),
				),
			),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('img', {name: 'invoiceClassification: Evaluated'})).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'calc-key-figures: Failed'})).toBeVisible();
	});

	it('should keep the latest repeated evaluation when instances arrive in lexical order', async ({worker}) => {
		mockPageWithTwoDecisions(worker);
		worker.use(
			mockDrdPage(
				FIRST_DRD_PAGE_SCHEMA,
				HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [
							DECISION_INSTANCE,
							createCalcKeyFigures(`${EVALUATION_KEY}-10`, 'FAILED'),
							createCalcKeyFigures(`${EVALUATION_KEY}-2`, 'EVALUATED'),
						],
					}),
				),
			),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('img', {name: 'invoiceClassification: Evaluated'})).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'calc-key-figures: Failed'})).toBeVisible();
	});

	it('should show an error in the DRD instead of a partial evaluation when a page fails', async ({worker}) => {
		mockPageWithTwoDecisions(worker);
		worker.use(
			mockDrdPage(
				FIRST_DRD_PAGE_SCHEMA,
				HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [DECISION_INSTANCE],
						page: {totalItems: 2, endCursor: 'page-1'},
					}),
				),
			),
			mockDrdPage(nextDrdPageSchema('page-1'), new HttpResponse(null, {status: 500})),
		);

		const screen = await renderPage();

		await expect.element(screen.getByTestId('drd').getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'invoiceClassification: Evaluated'})).not.toBeInTheDocument();
	});

	it('should focus the error heading after selecting a decision in the DRD that cannot be viewed', async ({worker}) => {
		mockPageWithTwoDecisions(worker);
		worker.use(
			mockDrdPage(
				FIRST_DRD_PAGE_SCHEMA,
				HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [DECISION_INSTANCE, createCalcKeyFigures('calc-1', 'FAILED')],
					}),
				),
			),
		);
		const screen = await renderWithRouter(DecisionInstanceOnRoute, {path: PATH, initialEntry: INITIAL_ENTRY});
		const calcKeyFigures = screen.getByRole('button', {name: 'Calculate Credit History Key Figures'});
		await expect.element(calcKeyFigures).toBeVisible();

		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);
		await userEvent.click(calcKeyFigures);

		await expect
			.element(screen.getByRole('heading', {name: 'You do not have permission to view this information'}))
			.toHaveFocus();
	});

	it('should keep the DRD closed after closing it and opening the page again', async ({worker}) => {
		mockPage(worker);
		const screen = await renderPage();
		await userEvent.click(screen.getByRole('button', {name: 'Close DRD panel'}));
		await expect.element(screen.getByRole('button', {name: 'Open decision requirements diagram'})).toBeVisible();
		await screen.unmount();

		const reopened = await renderPage();

		await expect.element(reopened.getByTestId('instance-header')).toBeVisible();
		await expect.element(reopened.getByTestId('drd')).not.toBeInTheDocument();
		await expect.element(reopened.getByRole('button', {name: 'Open decision requirements diagram'})).toBeVisible();
	});

	it('should refresh the input and output rows after selecting another decision table in the DRD', async ({worker}) => {
		mockPageWithTwoDecisions(worker);
		worker.use(
			mockDrdWithCalcKeyFigures(),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json({
					...DECISION_INSTANCE,
					evaluatedInputs: [{inputId: 'input', inputName: 'First input', inputValue: '1'}],
					matchedRules: [
						{
							ruleId: 'rule-1',
							ruleIndex: 1,
							evaluatedOutputs: [
								{outputId: 'output', outputName: 'First output', outputValue: '"one"', ruleId: null, ruleIndex: null},
							],
						},
					],
				}),
			}),
		);
		const screen = await renderWithRouter(DecisionInstanceOnRoute, {path: PATH, initialEntry: INITIAL_ENTRY});
		const inputs = screen.getByRole('region', {name: 'input variables'});
		const outputs = screen.getByRole('region', {name: 'output variables'});
		await expect.element(inputs.getByText('First input')).toBeVisible();
		await expect.element(outputs.getByText('First output')).toBeVisible();

		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json({
					...createCalcKeyFigures('calc-1', 'EVALUATED'),
					evaluatedInputs: [{inputId: 'input', inputName: 'Next input', inputValue: '2'}],
					matchedRules: [
						{
							ruleId: 'rule-1',
							ruleIndex: 1,
							evaluatedOutputs: [
								{outputId: 'output', outputName: 'Next output', outputValue: '"two"', ruleId: null, ruleIndex: null},
							],
						},
					],
				}),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Calculate Credit History Key Figures'}));

		await expect.element(inputs.getByText('Next input')).toBeVisible();
		await expect.element(outputs.getByText('Next output')).toBeVisible();
		await expect.element(inputs.getByText('First input')).not.toBeInTheDocument();
		await expect.element(outputs.getByText('First output')).not.toBeInTheDocument();
	});

	it('should show only the result after selecting a literal expression in the DRD', async ({worker}) => {
		mockPageWithTwoDecisions(worker);
		worker.use(
			mockDrdWithCalcKeyFigures(),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json({...DECISION_INSTANCE, result: '{"table":"matched"}'}),
			}),
		);
		const screen = await renderWithRouter(DecisionInstanceOnRoute, {path: PATH, initialEntry: INITIAL_ENTRY});
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"table":"matched"/);

		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json({
					...createCalcKeyFigures('calc-1', 'EVALUATED'),
					decisionDefinitionType: 'LITERAL_EXPRESSION',
					result: '{"literal":"selected"}',
				}),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Calculate Credit History Key Figures'}));

		await expect.element(screen.getByRole('heading', {name: 'Result'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Inputs and outputs'})).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"literal":"selected"/);
	});

	it('should keep the result tab selected after selecting another decision table in the DRD', async ({worker}) => {
		mockPageWithTwoDecisions(worker);
		worker.use(
			mockDrdWithCalcKeyFigures(),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json({...DECISION_INSTANCE, result: '{"table":"first"}'}),
			}),
		);
		const screen = await renderWithRouter(DecisionInstanceOnRoute, {path: PATH, initialEntry: INITIAL_ENTRY});
		await userEvent.click(screen.getByRole('tab', {name: 'Result'}));
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"table":"first"/);

		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json({
					...createCalcKeyFigures('calc-1', 'EVALUATED'),
					result: '{"table":"next"}',
				}),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Calculate Credit History Key Figures'}));

		await expect.element(screen.getByRole('tab', {name: 'Result'})).toHaveAttribute('aria-selected', 'true');
		await expect.element(screen.getByTestId('results-json-viewer')).toMatchTextContent(/"table":"next"/);
	});
});
