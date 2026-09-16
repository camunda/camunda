/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {createInstance} from 'i18next';
import {HttpResponse} from 'msw';
import {I18nextProvider} from 'react-i18next';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetDecisionDefinitionXmlEndpoint,
	mockGetDecisionInstanceEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {translationResources} from '#/shared/i18n';
import {DecisionPanel} from './DecisionPanel';

const DECISION_INSTANCE_ID = '4294980768';
const DECISION_DEFINITION_KEY = '2251799813685253';

function renderDecisionPanel() {
	return renderWithRouter(() => <DecisionPanel decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} />, {
		path: '/operate/decisions/$decisionInstanceId',
		initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
	});
}

async function createTranslations(language: 'de' | 'fr' | 'es') {
	const translations = createInstance();
	await translations.init({
		lng: language,
		resources: translationResources,
		interpolation: {escapeValue: false},
	});

	return translations;
}

describe('<DecisionPanel />', () => {
	it('should render decision table content', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
						decisionDefinitionId: 'invoiceClassification',
					}),
				),
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
	});

	it('should render literal expression content', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
						decisionDefinitionId: 'calc-key-figures',
						decisionDefinitionType: 'LITERAL_EXPRESSION',
					}),
				),
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByText(/avg_score/)).toBeVisible();
	});

	it.for(['de', 'fr', 'es'] as const)(
		'should localize the loaded decision panel landmark label for %s',
		async (language, {worker}) => {
			worker.use(
				mockGetDecisionInstanceEndpoint({
					successResponse: HttpResponse.json(
						createDecisionInstance({
							decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
							decisionDefinitionKey: DECISION_DEFINITION_KEY,
							decisionDefinitionId: 'invoiceClassification',
						}),
					),
				}),
				mockGetDecisionDefinitionXmlEndpoint({
					successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
				}),
			);
			const translations = await createTranslations(language);
			const screen = await renderWithRouter(
				() => (
					<I18nextProvider i18n={translations}>
						<DecisionPanel decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} />
					</I18nextProvider>
				),
				{
					path: '/operate/decisions/$decisionInstanceId',
					initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
				},
			);

			await expect
				.element(screen.getByRole('region', {name: translations.t('operate.decisionInstance.panel.label')}))
				.toBeVisible();
			await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
		},
	);

	it('should deduplicate matched rule indexes before highlighting', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
						matchedRules: [
							{
								ruleId: 'rule-1',
								ruleIndex: 1,
								evaluatedOutputs: [
									{
										outputId: 'out-1',
										outputName: 'classification',
										outputValue: '"ok"',
										ruleId: 'rule-1',
										ruleIndex: 1,
									},
								],
							},
							{
								ruleId: null,
								ruleIndex: null,
								evaluatedOutputs: [
									{outputId: 'out-2', outputName: 'classification', outputValue: '"ok"', ruleId: null, ruleIndex: null},
								],
							},
							{
								ruleId: 'rule-1',
								ruleIndex: 1,
								evaluatedOutputs: [
									{
										outputId: 'out-1',
										outputName: 'classification',
										outputValue: '"ok"',
										ruleId: 'rule-1',
										ruleIndex: 1,
									},
								],
							},
							{
								ruleId: 'rule-3',
								ruleIndex: 3,
								evaluatedOutputs: [
									{
										outputId: 'out-3',
										outputName: 'classification',
										outputValue: '"ok"',
										ruleId: 'rule-3',
										ruleIndex: 3,
									},
								],
							},
						],
					}),
				),
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
		const firstRuleCell = screen.getByText('< 250').element();
		const middleRuleCell = screen.getByText('[250..1000]').element();
		const thirdRuleCell = screen.getByText('> 1000').element();
		const firstRuleRow = firstRuleCell.closest('tr');
		const middleRuleRow = middleRuleCell.closest('tr');
		const thirdRuleRow = thirdRuleCell.closest('tr');
		if (
			firstRuleRow === null ||
			middleRuleRow === null ||
			thirdRuleRow === null ||
			!(firstRuleRow instanceof HTMLElement) ||
			!(middleRuleRow instanceof HTMLElement) ||
			!(thirdRuleRow instanceof HTMLElement)
		) {
			throw new Error('Expected decision table rows to be rendered');
		}

		await expect
			.poll(() => getComputedStyle(firstRuleRow).backgroundColor === getComputedStyle(thirdRuleRow).backgroundColor)
			.toBe(true);
		await expect
			.poll(() => getComputedStyle(firstRuleRow).backgroundColor === getComputedStyle(middleRuleRow).backgroundColor)
			.toBe(false);
	});

	it('should render incident banner fallback text for failed evaluations without details', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
						state: 'FAILED',
						evaluationFailure: null,
					}),
				),
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByTestId('incident-banner')).toBeVisible();
		await expect.element(screen.getByText('Evaluation failed with unknown error')).toBeVisible();
	});

	it('should render forbidden state when decision definition xml cannot be accessed', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
					}),
				),
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByText('Missing permissions to view the Definition')).toBeVisible();
		await expect
			.element(
				screen.getByText(
					'Please contact your organization owner or admin to give you the necessary permissions to read this definition',
				),
			)
			.toBeVisible();
	});

	it('should render generic error state when decision definition xml fetch fails', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
					}),
				),
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByText('Data could not be fetched')).toBeVisible();
	});

	it('should show loading and then error for a failed xml refetch with warm cache', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
					}),
				),
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
		worker.use(
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
				delay: 500,
			}),
		);
		const cachedDecisionDefinitionXmlQuery = screen.queryClient
			.getQueryCache()
			.find({queryKey: ['decisionDefinitionXml', DECISION_DEFINITION_KEY]});
		if (cachedDecisionDefinitionXmlQuery === undefined) {
			throw new Error('Expected decision definition xml query to be cached');
		}
		const refetchPromise = cachedDecisionDefinitionXmlQuery.fetch();

		await expect.element(screen.getByTestId('diagram-spinner')).toBeVisible();
		await refetchPromise.catch(() => undefined);
		await expect.element(screen.getByText('Data could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('Invoice Amount')).not.toBeInTheDocument();
	});

	it('should show loading state while the decision instance is still loading', async ({worker}) => {
		worker.use(
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(
					createDecisionInstance({
						decisionEvaluationInstanceKey: DECISION_INSTANCE_ID,
						decisionDefinitionKey: DECISION_DEFINITION_KEY,
					}),
				),
				delay: 500,
			}),
			mockGetDecisionDefinitionXmlEndpoint({
				successResponse: HttpResponse.text(DMN_XML_WITH_LITERAL_EXPRESSION_AND_HIGHLIGHTABLE_TABLE),
			}),
		);

		const screen = await renderDecisionPanel();

		await expect.element(screen.getByTestId('diagram-spinner')).toBeVisible();
		await expect.element(screen.getByText('Invoice Amount')).toBeVisible();
	});
});
