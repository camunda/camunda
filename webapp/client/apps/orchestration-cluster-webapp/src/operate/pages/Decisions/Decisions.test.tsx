/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {http, HttpResponse} from 'msw';
import {
	endpoints,
	queryDecisionInstancesRequestBodySchema,
	type QueryDecisionInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetDecisionDefinitionXmlEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {DecisionsHarness} from './DecisionsHarness';
import {mockQueryDecisionDefinitionsEndpointByFilter} from './mockQueryDecisionDefinitionsEndpointByFilter';

const DECISION_DEFINITIONS = HttpResponse.json(
	createQueryDecisionDefinitionsResponse({
		items: [
			createDecisionDefinition({name: 'Invoice Approval', decisionDefinitionId: 'invoice-approval', version: 2}),
			createDecisionDefinition({name: 'Invoice Approval', decisionDefinitionId: 'invoice-approval', version: 1}),
			createDecisionDefinition({name: 'Discount Rate', decisionDefinitionId: 'discount-rate', version: 1}),
		],
	}),
);

const EMPTY_DECISION_INSTANCES = HttpResponse.json(createQueryDecisionInstancesResponse());

function renderDecisionsPage(searchParams: Record<string, string> = {}, legacy = false) {
	const query = new URLSearchParams({
		...(legacy ? {} : {evaluated: 'true', failed: 'true'}),
		...searchParams,
	}).toString();
	return renderWithRouter(DecisionsHarness, {
		path: '/operate/decisions',
		initialEntry: `/operate/decisions?${query}`,
	});
}

describe('<Decisions />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the filter sections', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage();

		await expect.element(screen.getByText('Instances States')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).toBeVisible();
	});

	it('should disable the version dropdown until a decision is selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage();

		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toBeDisabled();
	});

	it('should enable the version dropdown once a decision is selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage({decisionDefinitionId: 'invoice-approval'});

		await expect.element(screen.getByRole('combobox', {name: 'Version'})).not.toBeDisabled();
	});

	it('should navigate resetting version when a decision is selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
		);

		const screen = await renderDecisionsPage({decisionDefinitionId: 'discount-rate', decisionDefinitionVersion: '1'});

		const nameCombobox = screen.getByRole('combobox', {name: 'Name'});
		await nameCombobox.click({force: true});
		await nameCombobox.fill('Invoice Approval');
		await userEvent.keyboard('{Enter}');

		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;
		await expect.poll(getSearch).toMatchObject({decisionDefinitionId: 'invoice-approval'});
		expect(getSearch().decisionDefinitionVersion).toBeUndefined();
	});

	it('should resolve a selected decision outside the loaded definitions page', async ({worker}) => {
		const selectedDefinition = createDecisionDefinition({
			name: 'Later Decision',
			decisionDefinitionId: 'later-decision',
			version: 2,
		});
		const olderDefinition = createDecisionDefinition({
			name: 'Later Decision',
			decisionDefinitionId: 'later-decision',
			version: 1,
		});
		worker.use(
			mockQueryDecisionDefinitionsEndpointByFilter({
				unfilteredResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [],
						page: {hasMoreTotalItems: true, totalItems: 1001},
					}),
				),
				filteredResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: [selectedDefinition]})),
				versionsResponses: [
					HttpResponse.json(
						createQueryDecisionDefinitionsResponse({
							items: [selectedDefinition],
							page: {totalItems: 2, endCursor: 'next-page', hasMoreTotalItems: true},
						}),
					),
					HttpResponse.json(
						createQueryDecisionDefinitionsResponse({
							items: [olderDefinition],
							page: {totalItems: 2},
						}),
					),
				],
			}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
		);

		const screen = await renderDecisionsPage({
			decisionDefinitionId: 'later-decision',
			decisionDefinitionVersion: '2',
		});

		await expect.element(screen.getByRole('heading', {name: 'Later Decision'})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Later Decision');
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('2');
		await userEvent.click(screen.getByRole('combobox', {name: 'Version'}));
		await expect.element(screen.getByRole('option', {name: '1'})).toBeVisible();
		expect((screen.router.state.location.search as Record<string, unknown>).decisionDefinitionId).toBe(
			'later-decision',
		);
	});

	it('should ask for a tenant when a decision version exists in multiple tenants', async ({worker}) => {
		const definitions = HttpResponse.json(
			createQueryDecisionDefinitionsResponse({
				items: [
					createDecisionDefinition({
						name: 'Invoice Approval',
						decisionDefinitionId: 'invoice-approval',
						version: 1,
						tenantId: '<tenant-A>',
					}),
					createDecisionDefinition({
						name: 'Invoice Approval',
						decisionDefinitionId: 'invoice-approval',
						version: 1,
						tenantId: '<tenant-B>',
					}),
				],
			}),
		);
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({successResponse: definitions}),
			mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
		);

		const screen = await renderDecisionsPage({
			decisionDefinitionId: 'invoice-approval',
			decisionDefinitionVersion: '1',
			tenantId: 'all',
		});

		await expect.element(screen.getByText('Decision "Invoice Approval" exists in more than one Tenant')).toBeVisible();
	});

	describe('legacy bookmarks', () => {
		it('should not query instances when a bookmarked filter has no selected states', async ({worker}) => {
			worker.use(mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}));

			const screen = await renderDecisionsPage({tenantId: '<default>'}, true);

			await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).not.toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).not.toBeChecked();
			await expect.element(screen.getByText('To see some results, select at least one Instance state')).toBeVisible();
		});

		it('should hide previous rows and deletion when returning to a no-state bookmark', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({
					successResponse: HttpResponse.json(
						createQueryDecisionInstancesResponse({
							items: [createDecisionInstance({decisionEvaluationInstanceKey: '123567'})],
						}),
					),
				}),
			);

			const screen = await renderDecisionsPage({}, true);
			await screen.router.navigate({
				to: '/operate/decisions',
				search: {evaluated: true, failed: true},
			});
			await expect.element(screen.getByTitle('View decision instance 123567')).toBeVisible();
			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row 123567'}), {force: true});
			await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeVisible();

			screen.router.history.back();
			await expect.element(screen.getByText('To see some results, select at least one Instance state')).toBeVisible();
			await expect.element(screen.getByTitle('View decision instance 123567')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();
		});

		it('should accept explicit false in a bookmarked URL without broadening the API filter', async ({worker}) => {
			let requestedFilter: QueryDecisionInstancesRequestBody['filter'];
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				http.post(endpoints.queryDecisionInstances.getUrl(), async ({request}) => {
					const body = queryDecisionInstancesRequestBodySchema.parse(await request.json());
					requestedFilter = body.filter;
					return EMPTY_DECISION_INSTANCES.clone();
				}),
			);

			const screen = await renderDecisionsPage({evaluated: 'false', failed: 'true'}, true);

			await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).not.toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).toBeChecked();
			await expect.poll(() => requestedFilter).toMatchObject({state: {$in: ['FAILED']}});
		});

		it('should preserve tenant and all-version selection in the instances request', async ({worker}) => {
			let requestedFilter: QueryDecisionInstancesRequestBody['filter'];
			worker.use(
				mockQueryDecisionDefinitionsEndpointByFilter({
					unfilteredResponse: DECISION_DEFINITIONS,
					filteredResponse: DECISION_DEFINITIONS,
				}),
				http.post(endpoints.queryDecisionInstances.getUrl(), async ({request}) => {
					const body = queryDecisionInstancesRequestBodySchema.parse(await request.json());
					requestedFilter = body.filter;
					return EMPTY_DECISION_INSTANCES.clone();
				}),
				mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			);

			const screen = await renderDecisionsPage(
				{
					decisionDefinitionId: 'invoice-approval',
					decisionDefinitionVersion: 'all',
					tenantId: '<default>',
					evaluated: 'true',
				},
				true,
			);

			await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).not.toBeChecked();
			await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('All versions');
			await expect
				.poll(() => requestedFilter)
				.toMatchObject({
					decisionDefinitionId: 'invoice-approval',
					tenantId: '<default>',
					state: {$in: ['EVALUATED']},
				});
			expect(requestedFilter?.decisionDefinitionVersion).toBeUndefined();
		});

		it('should preserve state selection through browser back and forward', async ({worker}) => {
			const requestedFilters: Array<QueryDecisionInstancesRequestBody['filter']> = [];
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				http.post(endpoints.queryDecisionInstances.getUrl(), async ({request}) => {
					const body = queryDecisionInstancesRequestBodySchema.parse(await request.json());
					requestedFilters.push(body.filter);
					return EMPTY_DECISION_INSTANCES.clone();
				}),
			);

			const screen = await renderDecisionsPage({evaluated: 'true'}, true);

			await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).not.toBeChecked();
			await expect.poll(() => requestedFilters.at(-1)).toMatchObject({state: {$in: ['EVALUATED']}});

			await userEvent.click(screen.getByText('Failed'));
			await expect.poll(() => requestedFilters.at(-1)).toMatchObject({state: {$in: ['EVALUATED', 'FAILED']}});

			screen.router.history.back();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).not.toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).toBeChecked();

			screen.router.history.forward();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).toBeChecked();
		});
	});

	describe('instance state checkboxes', () => {
		it('defaults to both evaluated and failed checked', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage();

			await expect.element(screen.getByRole('checkbox', {name: 'Evaluated'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Failed'})).toBeChecked();
		});

		it('updates the URL when a checkbox is unchecked', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage();
			const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

			// force: the checkbox's labelText is a Stack with an icon, which visually covers the
			// native input and fails real-browser click actionability (see Processes' own tests).
			await screen.getByRole('checkbox', {name: 'Failed'}).click({force: true});

			await expect.poll(() => getSearch().failed).toBe(false);
		});
	});

	describe('reset button', () => {
		it('is disabled at the default filter state', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage();

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
		});

		it('is enabled once a decision is selected', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage({decisionDefinitionId: 'invoice-approval'});

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).not.toBeDisabled();
		});

		it('is enabled once a non-default instance state checkbox is set', async ({worker}) => {
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryDecisionInstancesEndpoint({successResponse: EMPTY_DECISION_INSTANCES}),
			);

			const screen = await renderDecisionsPage({failed: 'false'});

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).not.toBeDisabled();
		});
	});
});
