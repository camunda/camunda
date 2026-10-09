/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {delay, http, HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {z} from 'zod';
import {
	endpoints as apiEndpoints,
	queryProcessDefinitionsRequestBodySchema,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '../../../src/vitest-modules/test-extend';
import {renderWithRouter} from '../../../src/vitest-modules/render-with-router';
import {render} from 'vitest-browser-react';
import {QueryClient, QueryClientProvider, useQuery} from '@tanstack/react-query';
import {selectedDefinitionsQuery} from '#/operate/shared/queries/processDefinitions.queries';
import {
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockCurrentUserEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '../../../shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '../../../shared-test-modules/api-mocks/process-definitions';
import {createGetProcessDefinitionStatisticsResponse} from '../../../shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML} from '../../../shared-test-modules/api-mocks/process-definition-xmls';
import {createQueryProcessInstancesResponse} from '../../../shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '../../../shared-test-modules/api-mocks/system-configuration';
import {createCurrentUser} from '../../../shared-test-modules/api-mocks/current-user';
import {ProcessesHarness} from '#/operate/pages/Processes/ProcessesHarness';

const DEFINITIONS = HttpResponse.json(
	createQueryProcessDefinitionsResponse({
		items: [
			createProcessDefinition({processDefinitionId: 'orders', version: 2, processDefinitionKey: '2002'}),
			createProcessDefinition({processDefinitionId: 'orders', version: 1, processDefinitionKey: '1001'}),
		],
	}),
);
const EMPTY_INSTANCES = HttpResponse.json(createQueryProcessInstancesResponse());
const EMPTY_STATISTICS = HttpResponse.json(createGetProcessDefinitionStatisticsResponse([]));
const INVALID_REQUEST = new HttpResponse(null, {status: 400});
const TENANT_A_VERSIONS = Array.from({length: 1000}, (_, index) =>
	createProcessDefinition({
		name: 'Orders',
		processDefinitionId: 'orders',
		processDefinitionKey: `tenant-a-${index + 1}`,
		version: index + 1,
		tenantId: '<tenant-A>',
	}),
);
const UNRELATED_DEFINITIONS = Array.from({length: 999}, (_, index) =>
	createProcessDefinition({processDefinitionId: `unrelated-${index}`, processDefinitionKey: `other-${index}`}),
);
const mountedScreens: Awaited<ReturnType<typeof renderWithRouter>>[] = [];

function SelectedDefinitionLookupStatus() {
	const {isError, isPending} = useQuery({
		...selectedDefinitionsQuery('orders', undefined, {retry: false}),
		retry: false,
	});
	return <div>{isError ? 'failed' : isPending ? 'loading' : 'ready'}</div>;
}

async function renderPage(search = '') {
	const params = new URLSearchParams(search);
	if (!params.has('active')) {
		params.set('active', 'false');
	}
	if (!params.has('incidents')) {
		params.set('incidents', 'false');
	}
	if (!params.has('suspended')) {
		params.set('suspended', 'false');
	}
	const screen = await renderWithRouter(ProcessesHarness, {
		path: '/operate/processes',
		initialEntry: `/operate/processes?${params.toString()}`,
	});
	mountedScreens.push(screen);
	return screen;
}

function registerElementFilterTests() {
	describe('Processes element filter', () => {
		let clientConfig = createSystemConfiguration();
		let restoreGetItem: () => void;

		beforeEach(() => {
			clientConfig = createSystemConfiguration();
			const originalGetItem = sessionStorage.getItem.bind(sessionStorage);
			const getItemSpy = vi
				.spyOn(sessionStorage, 'getItem')
				.mockImplementation((key) => (key === 'clientConfig' ? JSON.stringify(clientConfig) : originalGetItem(key)));
			restoreGetItem = () => getItemSpy.mockRestore();
		});

		afterEach(async () => {
			for (const screen of mountedScreens) {
				await screen.unmount();
				await screen.queryClient.cancelQueries();
				screen.queryClient.clear();
			}
			mountedScreens.length = 0;
			restoreGetItem();
		});

		it('should offer only sorted flow nodes, with their names or IDs, for a selected version', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: DEFINITIONS}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?process=orders&version=1');
			const element = screen.getByRole('combobox', {name: 'Element'});
			await expect.element(element).toBeEnabled();
			await userEvent.click(element);
			await expect.element(screen.getByRole('option', {name: 'end_event'})).toBeVisible();
			await expect.element(screen.getByRole('option', {name: 'Review invoice'})).toBeVisible();
			await expect.element(screen.getByRole('option', {name: 'start_event'})).toBeVisible();
			await expect.element(screen.getByRole('option', {name: 'Flow_0xb8om9'})).not.toBeInTheDocument();
			await userEvent.fill(element, 'INVOICE');
			await expect.element(screen.getByRole('option', {name: 'Review invoice'})).toBeVisible();
			await expect.element(screen.getByRole('option', {name: 'start_event'})).not.toBeInTheDocument();
		});

		it('should restore the selected element from the URL and send it with process, version and mixed states', async ({
			worker,
		}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: DEFINITIONS}),
				mockQueryProcessInstancesEndpoint({
					schema: z.object({
						filter: z.object({
							processDefinitionId: z.object({$eq: z.literal('orders')}),
							processDefinitionVersion: z.literal(1),
							$or: z.tuple([
								z.object({
									elementId: z.object({$eq: z.literal('task-1')}),
									elementInstanceState: z.object({$eq: z.literal('ACTIVE')}),
									state: z.object({$eq: z.literal('ACTIVE')}),
									hasIncident: z.literal(false),
								}),
								z.object({
									elementId: z.object({$eq: z.literal('task-1')}),
									elementInstanceState: z.object({$eq: z.literal('ACTIVE')}),
									state: z.object({$eq: z.literal('SUSPENDED')}),
								}),
								z.object({
									elementId: z.object({$eq: z.literal('task-1')}),
									state: z.object({$eq: z.literal('COMPLETED')}),
									hasIncident: z.literal(false),
								}),
								z.object({
									elementId: z.object({$eq: z.literal('task-1')}),
									elementInstanceState: z.object({$eq: z.literal('ACTIVE')}),
									state: z.object({$neq: z.literal('SUSPENDED')}),
									hasIncident: z.literal(true),
								}),
							]),
						}),
					}),
					successResponse: EMPTY_INSTANCES,
					failureResponse: INVALID_REQUEST,
				}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage(
				'?process=orders&version=1&elementId=task-1&active=true&incidents=true&suspended=true&completed=true',
			);

			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toHaveValue('Review invoice');
			await expect.element(screen.getByRole('checkbox', {name: 'Active'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Incidents'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Suspended'})).toBeChecked();
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
		});

		it('should select and clear an element via the combobox and follow browser history', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: DEFINITIONS}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?process=orders&version=1');
			const element = screen.getByRole('combobox', {name: 'Element'});
			const selected = () => (screen.router.state.location.search as Record<string, unknown>).elementId;
			await expect.element(element).toBeEnabled();
			await userEvent.fill(element, 'Review invoice');
			await userEvent.keyboard('{Enter}');
			await expect.poll(selected).toBe('task-1');
			await expect.element(element).toHaveValue('Review invoice');

			screen.router.history.back();
			await expect.poll(selected).toBeUndefined();
			await expect.element(element).toHaveValue('');
			screen.router.history.forward();
			await expect.poll(selected).toBe('task-1');
			await expect.element(element).toHaveValue('Review invoice');

			await userEvent.click(screen.getByRole('button', {name: 'Clear selected item'}).last());
			await expect.poll(selected).toBeUndefined();
		});

		it('should keep the instance list available when the XML fails and recover through retry', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: DEFINITIONS}),
				mockQueryProcessInstancesEndpoint({
					schema: z.object({filter: z.object({processDefinitionId: z.object({$eq: z.literal('orders')})})}),
					successResponse: EMPTY_INSTANCES,
					failureResponse: INVALID_REQUEST,
				}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status: 503})}),
			);

			const screen = await renderPage('?process=orders&version=1&active=true');
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
			await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();

			worker.use(
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);
			await userEvent.click(screen.getByRole('button', {name: 'Retry loading elements'}));
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeEnabled();
			await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).not.toBeInTheDocument();
		});

		it('should not offer stale cached options after a failed XML refresh', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: DEFINITIONS}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?process=orders&version=1&elementId=task-1');
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeEnabled();
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toHaveValue('Review invoice');
			worker.use(mockGetProcessDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status: 503})}));
			const xmlQuery = screen.queryClient.getQueryCache().find({queryKey: ['processDefinitionDiagram', '1001']});
			if (xmlQuery === undefined) {
				throw new Error('The selected definition XML query was not loaded');
			}
			await expect(xmlQuery.fetch()).rejects.toThrow();

			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toHaveValue('Review invoice');
			await expect.element(screen.getByRole('button', {name: 'Clear element filter'})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
		});

		it('should leave the element unavailable without a single version or with empty XML', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: DEFINITIONS}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?process=orders');
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
			screen.router.history.push('/operate/processes?process=orders&version=1');
			await expect.element(screen.getByText('No diagram available for this process')).toBeVisible();
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
		});

		it.for([
			{status: 503, isRetryAvailable: true},
			{status: 403, isRetryAvailable: false},
		])(
			'should show and allow clearing an active element filter when XML responds $status',
			async ({status, isRetryAvailable}, {worker}) => {
				worker.use(
					mockQueryProcessDefinitionsEndpoint({successResponse: DEFINITIONS}),
					mockQueryProcessInstancesEndpoint({
						schema: z.object({filter: z.object({elementId: z.object({$eq: z.literal('task-1')})})}),
						successResponse: EMPTY_INSTANCES,
						failureResponse: INVALID_REQUEST,
					}),
					mockGetProcessDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status})}),
				);
				const screen = await renderPage('?process=orders&version=1&elementId=task-1');
				const element = screen.getByRole('combobox', {name: 'Element'});
				await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
				await expect.element(element).toBeDisabled();
				await expect.element(element).toHaveValue('task-1');
				const retryButton = screen.getByRole('button', {name: 'Retry loading elements'});
				if (isRetryAvailable) {
					await expect.element(retryButton).toBeVisible();
				} else {
					await expect.element(retryButton).not.toBeInTheDocument();
					await expect.element(screen.getByText('Missing permissions to view the Definition')).toBeVisible();
				}
				worker.use(mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}));
				await userEvent.click(screen.getByRole('button', {name: 'Clear element filter'}));
				await expect
					.poll(() => (screen.router.state.location.search as Record<string, unknown>).elementId)
					.toBeUndefined();
				await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
			},
		);

		it.for([{otherTenantVersion: 1}])(
			'should not use another tenant XML when a process ID exists in different tenants at version $otherTenantVersion',
			async ({otherTenantVersion}, {worker}) => {
				clientConfig = createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
				});
				worker.use(
					mockCurrentUserEndpoint({
						successResponse: HttpResponse.json(
							createCurrentUser({
								tenants: [
									{tenantId: '<tenant-A>', name: 'Tenant A', description: null},
									{tenantId: '<tenant-B>', name: 'Tenant B', description: null},
								],
							}),
						),
					}),
					mockQueryProcessDefinitionsEndpoint({
						successResponse: HttpResponse.json(
							createQueryProcessDefinitionsResponse({
								items: [
									createProcessDefinition({
										name: 'Orders',
										processDefinitionId: 'orders',
										version: 1,
										tenantId: '<tenant-A>',
										processDefinitionKey: '1001',
									}),
									createProcessDefinition({
										name: 'Orders',
										processDefinitionId: 'orders',
										version: otherTenantVersion,
										tenantId: '<tenant-B>',
										processDefinitionKey: '2002',
									}),
								],
							}),
						),
					}),
					mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				);

				const screen = await renderPage('?tenantId=all&process=orders&version=1');
				await expect.element(screen.getByText('Process "Orders" exists in more than one Tenant')).toBeVisible();
				await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
				await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
				await userEvent.click(screen.getByRole('combobox', {name: 'Version'}));
				await expect.element(screen.getByRole('option', {name: '1'})).toBeVisible();
				await userEvent.keyboard('{Escape}');
				screen.router.history.push('/operate/processes?tenantId=all&process=orders');
				await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('All versions');
				await expect.element(screen.getByText('Process "Orders" exists in more than one Tenant')).toBeVisible();
				await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
				screen.router.history.push('/operate/processes?tenantId=all&process=orders&version=3');
				await expect.element(screen.getByText('There is no Process selected')).toBeVisible();
				screen.router.history.back();
				await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('All versions');
				screen.router.history.back();
				await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('1');
				await expect.element(screen.getByText('Process "Orders" exists in more than one Tenant')).toBeVisible();
				screen.router.history.forward();
				await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('All versions');
			},
		);

		it('should offer elements from a tenant-scoped definition', async ({worker}) => {
			clientConfig = createSystemConfiguration({
				deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
			});
			worker.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(
						createCurrentUser({
							tenants: [{tenantId: '<tenant-B>', name: 'Tenant B', description: null}],
						}),
					),
				}),
				mockQueryProcessDefinitionsEndpoint({
					schema: z.object({filter: z.object({tenantId: z.literal('<tenant-B>')})}),
					successResponse: HttpResponse.json(
						createQueryProcessDefinitionsResponse({
							items: [
								createProcessDefinition({
									processDefinitionId: 'orders',
									version: 2,
									tenantId: '<tenant-B>',
									processDefinitionKey: '2002',
								}),
							],
						}),
					),
					failureResponse: INVALID_REQUEST,
				}),
				mockQueryProcessInstancesEndpoint({
					schema: z.object({filter: z.object({tenantId: z.object({$eq: z.literal('<tenant-B>')})})}),
					successResponse: EMPTY_INSTANCES,
					failureResponse: INVALID_REQUEST,
				}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?tenantId=%3Ctenant-B%3E&process=orders&version=2');
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeEnabled();
			await userEvent.click(screen.getByRole('combobox', {name: 'Element'}));
			await expect.element(screen.getByRole('option', {name: 'Review invoice'})).toBeVisible();
		});

		it('should wait for every selected-process page before allowing XML from any tenant', async ({worker}) => {
			clientConfig = createSystemConfiguration({
				deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
			});
			const tenantB = createProcessDefinition({
				name: 'Orders',
				processDefinitionId: 'orders',
				processDefinitionKey: 'tenant-b-1',
				version: 1,
				tenantId: '<tenant-B>',
			});
			worker.use(
				mockCurrentUserEndpoint({
					successResponse: HttpResponse.json(
						createCurrentUser({
							tenants: [
								{tenantId: '<tenant-A>', name: 'Tenant A', description: null},
								{tenantId: '<tenant-B>', name: 'Tenant B', description: null},
							],
						}),
					),
				}),
				http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
					const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
					const processFilter = body.filter?.processDefinitionId;
					if ((typeof processFilter === 'string' ? processFilter : processFilter?.$eq) === 'orders') {
						if (body.page?.after === 'orders-next') {
							await delay(1500);
							return HttpResponse.json(
								createQueryProcessDefinitionsResponse({
									items: [tenantB],
									page: {totalItems: 1001, hasMoreTotalItems: false},
								}),
							);
						}
						return HttpResponse.json(
							createQueryProcessDefinitionsResponse({
								items: TENANT_A_VERSIONS,
								page: {totalItems: 1001, hasMoreTotalItems: true, endCursor: 'orders-next'},
							}),
						);
					}
					return HttpResponse.json(
						createQueryProcessDefinitionsResponse({
							items: [TENANT_A_VERSIONS[0]!, ...UNRELATED_DEFINITIONS],
							page: {totalItems: 2000, hasMoreTotalItems: true, endCursor: 'global-next'},
						}),
					);
				}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
			);

			const screen = await renderPage('?tenantId=all&process=orders&version=1');
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
			await expect.element(screen.getByTestId('diagram-spinner')).toBeVisible();
			await expect.element(screen.getByText('Process "Orders" exists in more than one Tenant')).toBeVisible();
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
		});

		it('should find an otherwise truncated unique process through a filtered lookup', async ({worker}) => {
			const unique = createProcessDefinition({
				name: 'Unique Process',
				processDefinitionId: 'unique',
				processDefinitionKey: 'unique-1',
				version: 1,
			});
			worker.use(
				http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
					const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
					const processFilter = body.filter?.processDefinitionId;
					return HttpResponse.json(
						(typeof processFilter === 'string' ? processFilter : processFilter?.$eq) === 'unique'
							? createQueryProcessDefinitionsResponse({items: [unique]})
							: createQueryProcessDefinitionsResponse({
									items: [...UNRELATED_DEFINITIONS, TENANT_A_VERSIONS[0]!],
									page: {totalItems: 1001, hasMoreTotalItems: true, endCursor: 'global-next'},
								}),
					);
				}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?process=unique&version=1&elementId=task-1');
			const getSearch = () => screen.router.state.location.search as Record<string, unknown>;
			await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Unique Process');
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeEnabled();
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toHaveValue('Review invoice');
			expect(getSearch()).toMatchObject({process: 'unique', version: 1, elementId: 'task-1'});
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
			await userEvent.click(screen.getByRole('combobox', {name: 'Version'}));
			await userEvent.click(screen.getByRole('option', {name: 'All versions'}));
			await expect.poll(() => getSearch().version).toBeUndefined();
			await expect.poll(() => getSearch().elementId).toBeUndefined();
			expect(getSearch().process).toBe('unique');
			await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
		});

		it('should keep instances available and the element clearable through selected-definition lookup failures', async ({
			worker,
		}) => {
			let lookupFailureStatus: 503 | 403 | undefined = 503;
			let isLookupSlow = false;
			worker.use(
				http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
					const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
					const processFilter = body.filter?.processDefinitionId;
					if ((typeof processFilter === 'string' ? processFilter : processFilter?.$eq) === 'orders' && isLookupSlow) {
						await delay(1200);
					}
					return (typeof processFilter === 'string' ? processFilter : processFilter?.$eq) === 'orders' &&
						lookupFailureStatus !== undefined
						? new HttpResponse(null, {status: lookupFailureStatus})
						: DEFINITIONS.clone();
				}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?process=orders&version=1&elementId=task-1');
			const element = screen.getByRole('combobox', {name: 'Element'});
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
			await expect.element(element).toHaveValue('task-1');
			await expect.element(element).toBeDisabled();
			await expect.element(screen.getByRole('button', {name: 'Clear element filter'})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();

			lookupFailureStatus = undefined;
			await userEvent.click(screen.getByRole('button', {name: 'Retry loading elements'}));
			await expect.element(element).toBeEnabled();
			await expect.element(element).toHaveValue('Review invoice');

			isLookupSlow = true;
			const refresh = screen.queryClient.refetchQueries({queryKey: ['operationsLogDefinitions']});
			await expect.poll(() => screen.queryClient.isFetching({queryKey: ['operationsLogDefinitions']})).toBe(1);
			await expect.element(element).toBeEnabled();
			await expect.element(element).toHaveValue('Review invoice');
			await refresh;
			isLookupSlow = false;

			lookupFailureStatus = 503;
			await screen.queryClient.refetchQueries({queryKey: ['operationsLogDefinitions']});
			await expect.element(element).toBeDisabled();
			await expect.element(element).toHaveValue('task-1');
			await expect.element(screen.getByRole('button', {name: 'Clear element filter'})).toBeVisible();
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();

			lookupFailureStatus = undefined;
			await userEvent.click(screen.getByRole('button', {name: 'Retry loading elements'}));
			await expect.element(element).toBeEnabled();
			await expect.element(element).toHaveValue('Review invoice');

			lookupFailureStatus = 403;
			await screen.queryClient.refetchQueries({queryKey: ['operationsLogDefinitions']});
			await expect.element(element).toBeDisabled();
			await expect.element(screen.getByText('Missing permissions to view the Definition')).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Clear element filter'})).toBeVisible();
		});

		it('should reject an incomplete selected-process page and recover without hiding instances', async ({worker}) => {
			let isCursorMissing = true;
			worker.use(
				http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
					const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
					const processFilter = body.filter?.processDefinitionId;
					return HttpResponse.json(
						(typeof processFilter === 'string' ? processFilter : processFilter?.$eq) === 'orders' && isCursorMissing
							? createQueryProcessDefinitionsResponse({
									items: [createProcessDefinition({processDefinitionId: 'orders', version: 1})],
									page: {totalItems: 2, hasMoreTotalItems: true, endCursor: null},
								})
							: createQueryProcessDefinitionsResponse({
									items: [createProcessDefinition({processDefinitionId: 'orders', version: 1})],
								}),
					);
				}),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
				mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
			);

			const screen = await renderPage('?process=orders&version=1&elementId=task-1');
			const element = screen.getByRole('combobox', {name: 'Element'});
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
			await expect.element(element).toHaveValue('task-1');
			await expect.element(element).toBeDisabled();
			await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();

			isCursorMissing = false;
			await userEvent.click(screen.getByRole('button', {name: 'Retry loading elements'}));
			await expect.element(element).toBeEnabled();
			await expect.element(element).toHaveValue('Review invoice');
		});

		it.for([{hasMoreTotalItems: true}, {hasMoreTotalItems: false}])(
			'should handle a terminal empty selected-process page with hasMoreTotalItems=$hasMoreTotalItems',
			async ({hasMoreTotalItems}, {worker}) => {
				worker.use(
					http.post(apiEndpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
						const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
						const processFilter = body.filter?.processDefinitionId;
						return HttpResponse.json(
							(typeof processFilter === 'string' ? processFilter : processFilter?.$eq) === 'orders'
								? body.page?.after
									? createQueryProcessDefinitionsResponse({
											items: [],
											page: {totalItems: hasMoreTotalItems ? 10000 : 2, hasMoreTotalItems},
										})
									: createQueryProcessDefinitionsResponse({
											items: [createProcessDefinition({processDefinitionId: 'orders', version: 1})],
											page: {
												totalItems: hasMoreTotalItems ? 10000 : 2,
												hasMoreTotalItems: true,
												endCursor: 'next',
											},
										})
								: createQueryProcessDefinitionsResponse({
										items: [createProcessDefinition({processDefinitionId: 'orders', version: 1})],
									}),
						);
					}),
					mockQueryProcessInstancesEndpoint({successResponse: EMPTY_INSTANCES}),
					mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
					mockGetProcessDefinitionStatisticsEndpoint({successResponse: EMPTY_STATISTICS}),
				);

				const screen = await renderPage('?process=orders&version=1');
				if (hasMoreTotalItems) {
					await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeEnabled();
					await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).not.toBeInTheDocument();
				} else {
					await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
					await expect.element(screen.getByRole('button', {name: 'Retry loading elements'})).toBeVisible();
				}
			},
		);

		it.for([{isWarm: false}, {isWarm: true}])(
			'should not retry a forbidden selected-definition page with warm cache $isWarm',
			async ({isWarm}, {worker}) => {
				let isForbidden = !isWarm;
				const requests = vi.fn();
				worker.use(
					http.post(apiEndpoints.queryProcessDefinitions.getUrl(), () => {
						requests();
						return isForbidden
							? new HttpResponse(null, {status: 403})
							: HttpResponse.json(createQueryProcessDefinitionsResponse({items: TENANT_A_VERSIONS.slice(0, 1)}));
					}),
				);
				const queryClient = new QueryClient({defaultOptions: {queries: {retry: 3, retryDelay: 0}}});
				const screen = await render(
					<QueryClientProvider client={queryClient}>
						<SelectedDefinitionLookupStatus />
					</QueryClientProvider>,
				);
				if (isWarm) {
					await expect.element(screen.getByText('ready')).toBeVisible();
					isForbidden = true;
					await queryClient.refetchQueries({queryKey: ['operationsLogDefinitions']});
				}
				await expect.element(screen.getByText('failed')).toBeVisible();
				expect(requests).toHaveBeenCalledTimes(isWarm ? 2 : 1);
				await screen.unmount();
				await queryClient.cancelQueries();
			},
		);
	});
}

export {registerElementFilterTests};
