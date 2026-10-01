/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {http, HttpResponse} from 'msw';
import {z} from 'zod';
import {
	createMemoryHistory,
	createRootRoute,
	createRoute,
	createRouter,
	parseSearchWith,
	stringifySearchWith,
	useSearch,
} from '@tanstack/react-router';
import {
	endpoints,
	queryAuditLogsRequestBodySchema,
	queryProcessDefinitionsRequestBodySchema,
	type QueryProcessDefinitionsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {parseSearchValueSafe} from '#/shared/parseSearchValueSafe';
import {
	mockCurrentUserEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createGetProcessDefinitionResponse,
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {createQueryDecisionDefinitionsResponse} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {logger} from '#/operate/shared/utils/logger';
import {OperationsLog} from './OperationsLog';
import {operationsLogSearchSchema, stripLegacyFilters} from './operationsLog.schema';

type PageWith<T, K extends string> = T extends unknown ? (K extends keyof T ? T : never) : never;
function isCursorForwardPagination<T extends object>(page: T | undefined): page is PageWith<T, 'after'> {
	return page !== undefined && 'after' in page;
}

const TENANT_A = '<tenant-A>';
const TENANT_B = '<tenant-B>';
const A_VERSION_1 = createProcessDefinition({
	processDefinitionId: 'invoice',
	processDefinitionKey: 'key-A-1',
	name: 'Invoice A',
	tenantId: TENANT_A,
	version: 1,
});
const B_VERSION_2 = createProcessDefinition({
	processDefinitionId: 'invoice',
	processDefinitionKey: 'key-B-2',
	name: 'Invoice B',
	tenantId: TENANT_B,
	version: 2,
});
const OTHER_A = createProcessDefinition({
	processDefinitionId: 'shipment',
	processDefinitionKey: 'shipment-A-5',
	name: 'Shipment',
	tenantId: TENANT_A,
	version: 5,
});
const EMPTY_AUDIT = HttpResponse.json(createQueryAuditLogsResponse());

function OperationsLogFromUrl() {
	const search = useSearch({strict: false});
	return <OperationsLog {...operationsLogSearchSchema.parse(search)} />;
}

function renderPage(query = '') {
	return renderWithRouter(OperationsLogFromUrl, {
		path: '/operate/operations-log',
		initialEntry: `/operate/operations-log${query}`,
	});
}

async function createSearchRouter(initialEntry: string) {
	const root = createRootRoute();
	const route = createRoute({
		getParentRoute: () => root,
		path: '/operate/operations-log',
		validateSearch: operationsLogSearchSchema,
		search: {middlewares: [stripLegacyFilters]},
	});
	const router = createRouter({
		routeTree: root.addChildren([route]),
		history: createMemoryHistory({initialEntries: [initialEntry]}),
		parseSearch: parseSearchWith(parseSearchValueSafe),
		stringifySearch: stringifySearchWith(JSON.stringify, parseSearchValueSafe),
	});
	await router.load();
	return router;
}

describe('Operations Log saved filters', () => {
	beforeEach(() => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		);
	});

	afterEach(() => {
		sessionStorage.clear();
		vi.restoreAllMocks();
	});

	it.for([
		{
			query: {
				processDefinitionId: 'invoice',
				processDefinitionVersion: '2',
				operationType: 'CREATE,UPDATE',
				entityType: 'JOB,VARIABLE',
			},
			version: 2,
		},
		{
			query: {
				processDefinitionId: 'invoice',
				processDefinitionVersion: 'all',
				operationType: 'CREATE,UPDATE',
				entityType: 'JOB,VARIABLE',
			},
			version: undefined,
		},
	])('should normalize legacy process and multi-select filters with version $version', ({query, version}) => {
		const search = operationsLogSearchSchema.parse(query);

		expect(search).toMatchObject({
			process: 'invoice',
			operationType: ['CREATE', 'UPDATE'],
			entityType: ['JOB', 'VARIABLE'],
		});
		expect(search.version).toBe(version);
		expect(search.allVersions).toBe(version === undefined ? true : undefined);
		expect(search).not.toHaveProperty('processDefinitionId');
		expect(search).not.toHaveProperty('processDefinitionVersion');
	});

	it.for([
		{version: 'invalid', operationType: 'CREATE', entityType: 'JOB'},
		{version: '2', operationType: 'CREATE,INVALID', entityType: 'JOB'},
		{version: '2', operationType: 'CREATE', entityType: 'JOB,INVALID'},
	])(
		'should reject an invalid saved version or multi-select rather than broaden the query',
		({version, operationType, entityType}) => {
			expect(
				operationsLogSearchSchema.safeParse({process: 'invoice', version, operationType, entityType}).success,
			).toBe(false);
		},
	);

	it.for(['version', 'processDefinitionVersion'] as const)(
		'should reject a boolean %s rather than search version 1',
		(field) => {
			expect(operationsLogSearchSchema.safeParse({processDefinitionId: 'invoice', [field]: true}).success).toBe(false);
		},
	);

	it.for(['?version=2', '?allVersions=true', '?processDefinitionVersion=2', '?processDefinitionVersion=all'])(
		'should reject orphaned %s without querying audit logs',
		async (query, {worker}) => {
			const auditRequests = vi.fn(() => EMPTY_AUDIT.clone());
			worker.use(http.post(endpoints.queryAuditLogs.getUrl(), auditRequests));

			const screen = await renderPage(query);

			await expect.element(screen.getByText(/A process is required for a version filter/)).toBeVisible();
			expect(auditRequests).not.toHaveBeenCalled();
		},
	);

	it('should preserve numeric-looking process IDs from legacy and canonical links', () => {
		expect(operationsLogSearchSchema.parse({processDefinitionId: 123, tenantId: TENANT_A}).process).toBe('123');
		expect(operationsLogSearchSchema.parse({process: 123, tenantId: TENANT_A}).process).toBe('123');
	});

	it('should strip legacy aliases on navigation and preserve filters through reload, Back and Forward', async () => {
		const router = await createSearchRouter(
			'/operate/operations-log?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=2&operationType=CREATE,UPDATE',
		);
		expect(router.state.matches.at(-1)?.search).toMatchObject({
			tenantId: TENANT_B,
			process: 'invoice',
			version: 2,
			operationType: ['CREATE', 'UPDATE'],
		});

		await router.navigate({
			to: '/operate/operations-log',
			search: (search) => ({...search, process: 'shipment', version: 5}),
		});
		const updatedHref = router.state.location.href;
		expect(updatedHref).not.toContain('processDefinitionId');
		expect(updatedHref).not.toContain('processDefinitionVersion');
		expect(router.state.matches.at(-1)?.search).toMatchObject({
			tenantId: TENANT_B,
			process: 'shipment',
			version: 5,
			operationType: ['CREATE', 'UPDATE'],
		});

		router.history.back();
		await router.load();
		expect(router.state.matches.at(-1)?.search).toMatchObject({process: 'invoice', version: 2});
		router.history.forward();
		await router.load();
		expect(router.state.location.href).toBe(updatedHref);
		const reloaded = await createSearchRouter(updatedHref);
		expect(reloaded.state.matches.at(-1)?.search).toMatchObject({
			tenantId: TENANT_B,
			process: 'shipment',
			version: 5,
			operationType: ['CREATE', 'UPDATE'],
		});
	});

	it('should retain an explicit legacy All versions choice when canonicalizing the URL', async () => {
		const router = await createSearchRouter(
			'/operate/operations-log?tenantId=%3Ctenant-A%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		await router.navigate({to: '/operate/operations-log', search: (search) => ({...search, actorId: 'operator'})});

		expect(router.state.location.href).not.toContain('processDefinitionVersion');
		const reloaded = await createSearchRouter(router.state.location.href);
		expect(reloaded.state.matches.at(-1)?.search).toMatchObject({
			tenantId: TENANT_A,
			process: 'invoice',
			allVersions: true,
			actorId: 'operator',
		});
	});

	it('should resolve a legacy link in the selected tenant beyond the first page', async ({worker}) => {
		const requests: QueryProcessDefinitionsRequestBody[] = [];
		const auditFilters: unknown[] = [];
		const otherProcessInTenant = createProcessDefinition({
			...OTHER_A,
			processDefinitionKey: 'shipment-B-5',
			tenantId: TENANT_B,
		});
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				requests.push(body);
				if (body.filter?.isLatestVersion) {
					return HttpResponse.json(
						createQueryProcessDefinitionsResponse(
							isCursorForwardPagination(body.page) && body.page.after
								? {items: [B_VERSION_2]}
								: {
										items: [otherProcessInTenant],
										page: {totalItems: 2, hasMoreTotalItems: false, endCursor: 'picker-page-2'},
									},
						),
					);
				}
				if (Object.keys(body.filter ?? {}).length === 0) {
					return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]}));
				}
				const definitionId = body.filter?.processDefinitionId;
				if (body.filter?.tenantId !== TENANT_B || typeof definitionId === 'string' || definitionId?.$eq !== 'invoice') {
					return new HttpResponse(null, {status: 400});
				}
				if (body.filter.version === undefined) {
					return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [B_VERSION_2]}));
				}
				if (body.filter.version !== 2) {
					return new HttpResponse(null, {status: 400});
				}
				return HttpResponse.json(
					createQueryProcessDefinitionsResponse(
						isCursorForwardPagination(body.page) && body.page.after
							? {items: [B_VERSION_2]}
							: {items: [], page: {totalItems: 1, hasMoreTotalItems: false, endCursor: 'page-2'}},
					),
				);
			}),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: TENANT_A, name: 'Tenant A', description: null},
							{tenantId: TENANT_B, name: 'Tenant B', description: null},
						],
					}),
				),
			}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			http.post(endpoints.queryAuditLogs.getUrl(), async ({request}) => {
				auditFilters.push(queryAuditLogsRequestBodySchema.parse(await request.json()).filter);
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=2&operationType=CREATE,UPDATE&entityType=JOB,VARIABLE',
		);

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice B');
		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).toMatchTextContent('Tenant B');
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toBeEnabled();
		expect(requests.filter((body) => body.filter?.isLatestVersion)).toEqual(
			expect.arrayContaining([
				expect.objectContaining({filter: {isLatestVersion: true, tenantId: TENANT_B}}),
				expect.objectContaining({
					filter: {isLatestVersion: true, tenantId: TENANT_B},
					page: expect.objectContaining({after: 'picker-page-2'}),
				}),
			]),
		);
		expect(requests.filter((body) => body.filter?.isLatestVersion)).toHaveLength(2);
		expect(requests.filter((body) => body.filter?.processDefinitionId)).toEqual(
			expect.arrayContaining([
				expect.objectContaining({filter: {processDefinitionId: {$eq: 'invoice'}, tenantId: TENANT_B, version: 2}}),
				expect.objectContaining({filter: {processDefinitionId: {$eq: 'invoice'}, tenantId: TENANT_B}}),
				expect.objectContaining({
					filter: {processDefinitionId: {$eq: 'invoice'}, tenantId: TENANT_B, version: 2},
					page: expect.objectContaining({after: 'page-2'}),
				}),
			]),
		);
		expect(requests.filter((body) => body.filter?.processDefinitionId)).toHaveLength(3);
		await expect
			.poll(() => auditFilters)
			.toMatchObject([
				{
					processDefinitionKey: 'key-B-2',
					tenantId: TENANT_B,
					operationType: {$in: ['CREATE', 'UPDATE']},
					entityType: {$in: ['JOB', 'VARIABLE']},
				},
			]);
	});

	it('should resolve historical process names by key without loading every definition', async ({worker}) => {
		const definitionSearches: QueryProcessDefinitionsRequestBody[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				definitionSearches.push(body);
				return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [B_VERSION_2]}));
			}),
			http.get(endpoints.getProcessDefinition.getUrl({processDefinitionKey: 'key-B-1'}), () =>
				HttpResponse.json(
					createGetProcessDefinitionResponse({
						...B_VERSION_2,
						processDefinitionKey: 'key-B-1',
						version: 1,
						name: 'Historic Invoice',
					}),
				),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								entityKey: '42',
								processInstanceKey: '42',
								processDefinitionKey: 'key-B-1',
								entityType: 'PROCESS_INSTANCE',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('Historic Invoice')).toBeVisible();
		expect(definitionSearches).toEqual([]);
	});

	it('should retain a process ID when its definition name cannot be loaded', async ({worker}) => {
		const failedLookups = vi.fn();
		const logError = vi.spyOn(logger, 'error');
		worker.use(
			http.get(endpoints.getProcessDefinition.getUrl({processDefinitionKey: 'key-B-1'}), () => {
				failedLookups();
				return new HttpResponse(null, {status: 503});
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								entityKey: '42',
								processInstanceKey: '42',
								processDefinitionId: 'invoice',
								processDefinitionKey: 'key-B-1',
								entityType: 'PROCESS_INSTANCE',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect.poll(() => failedLookups.mock.calls.length).toBe(1);
		await expect.poll(() => logError.mock.calls.length).toBe(1);
		await expect.element(screen.getByText('invoice')).toBeVisible();
	});

	it('should stop after a capped-count lookup returns its final empty page', async ({worker}) => {
		const requests: QueryProcessDefinitionsRequestBody[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				requests.push(body);
				if (body.filter?.processDefinitionId) {
					return HttpResponse.json(
						createQueryProcessDefinitionsResponse(
							isCursorForwardPagination(body.page) && body.page.after
								? {items: [], page: {totalItems: 10000, hasMoreTotalItems: true}}
								: {items: [A_VERSION_1], page: {totalItems: 10000, hasMoreTotalItems: true, endCursor: 'end'}},
						),
					);
				}
				return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]}));
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({
				schema: z.object({filter: z.object({processDefinitionKey: z.literal('key-A-1')})}),
				successResponse: EMPTY_AUDIT,
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
		expect(requests.filter((body) => body.filter?.processDefinitionId && body.filter.version === 1)).toHaveLength(2);
		expect(
			requests.filter((body) => body.filter?.processDefinitionId && body.filter.version === undefined),
		).toHaveLength(2);
	});

	it('should require a tenant before offering processes in a multi-tenant deployment', async ({worker}) => {
		const latestRequests = vi.fn();
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () => {
				latestRequests();
				return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]}));
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('No operation log items yet')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();
		expect(latestRequests).not.toHaveBeenCalled();
	});

	it('should load cross-tenant process options only when All tenants is explicitly selected', async ({worker}) => {
		const latestRequests: QueryProcessDefinitionsRequestBody[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				latestRequests.push(queryProcessDefinitionsRequestBodySchema.parse(await request.json()));
				return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]}));
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=all');

		const processField = screen.getByRole('combobox', {name: 'Name'});
		await expect.element(processField).toBeEnabled();
		await userEvent.fill(processField, 'Invoice');
		await expect.element(screen.getByRole('option', {name: /Invoice A/})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: /Invoice B/})).toBeVisible();
		expect(latestRequests).toEqual([expect.objectContaining({filter: {isLatestVersion: true}})]);
	});

	it('should offer only versions belonging to the selected tenant', async ({worker}) => {
		const B_VERSION_4 = createProcessDefinition({...B_VERSION_2, processDefinitionKey: 'key-B-4', version: 4});
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2, B_VERSION_4]})),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-B%3E&process=invoice&version=2');
		const versionField = screen.getByRole('combobox', {name: 'Version'});
		await expect.element(versionField).toBeEnabled();
		versionField.element().focus();
		await userEvent.keyboard('{Space}');

		await expect.element(screen.getByRole('option', {name: '4'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: '1'})).not.toBeInTheDocument();
	});

	it('should not offer All versions for a single-version definition unless explicitly selected', async ({worker}) => {
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]})),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');
		const versionField = screen.getByRole('combobox', {name: 'Version'});
		await expect.element(versionField).toBeEnabled();
		versionField.element().focus();
		await userEvent.keyboard('{Space}');

		await expect.element(screen.getByRole('option', {name: 'All versions'})).not.toBeInTheDocument();
	});

	it('should never query an unscoped audit log when the selected version is missing', async ({worker}) => {
		const auditRequests = vi.fn();
		const resolutionRequests: QueryProcessDefinitionsRequestBody[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				if (body.filter?.version !== undefined) {
					resolutionRequests.push(body);
				}
				return HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: body.filter?.isLatestVersion ? [A_VERSION_1] : [],
					}),
				);
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			http.post(endpoints.queryAuditLogs.getUrl(), () => {
				auditRequests();
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&processDefinitionId=invoice&processDefinitionVersion=42');

		await expect
			.element(screen.getByText('The selected process definition or tenant could not be found'))
			.toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice A');
		expect(operationsLogSearchSchema.parse(screen.router.state.location.search).version).toBe(42);
		expect(resolutionRequests).toEqual([
			expect.objectContaining({filter: {processDefinitionId: {$eq: 'invoice'}, tenantId: TENANT_A, version: 42}}),
		]);
		expect(auditRequests).not.toHaveBeenCalled();
	});

	it('should block a selected version when process resolution fails even if decision names fail', async ({worker}) => {
		const auditRequests = vi.fn();
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 503})}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 503})}),
			http.post(endpoints.queryAuditLogs.getUrl(), () => {
				auditRequests();
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');

		await expect.element(screen.getByText("Couldn't load the selected process definition")).toBeVisible();
		await expect.element(screen.getByText('Operations Log', {exact: true})).not.toBeInTheDocument();
		expect(auditRequests).not.toHaveBeenCalled();
	});

	it('should retain an ambiguous tenantless selection without choosing a tenant or querying audit logs', async ({
		worker,
	}) => {
		const auditRequests = vi.fn();
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			http.post(endpoints.queryAuditLogs.getUrl(), () => {
				auditRequests();
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=all');

		await expect
			.element(screen.getByText('The selected process definition or tenant could not be found'))
			.toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('invoice');
		expect(auditRequests).not.toHaveBeenCalled();
	});

	it('should resolve a tenantless saved version when only one tenant has that version', async ({worker}) => {
		const auditFilters: unknown[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			http.post(endpoints.queryAuditLogs.getUrl(), async ({request}) => {
				auditFilters.push(queryAuditLogsRequestBodySchema.parse(await request.json()).filter);
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=2');

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
		await expect.poll(() => auditFilters).toMatchObject([{tenantId: TENANT_B, processDefinitionKey: 'key-B-2'}]);
	});

	it('should not pick a tenant when the requested version exists in multiple tenants', async ({worker}) => {
		const auditRequests = vi.fn();
		const lookupRequests: QueryProcessDefinitionsRequestBody[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				if (body.filter?.version === 2) {
					lookupRequests.push(body);
					return HttpResponse.json(
						createQueryProcessDefinitionsResponse(
							isCursorForwardPagination(body.page) && body.page.after
								? {items: [B_VERSION_2]}
								: {
										items: [createProcessDefinition({...A_VERSION_1, version: 2})],
										page: {totalItems: 2, hasMoreTotalItems: false, endCursor: 'second-tenant'},
									},
						),
					);
				}
				return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]}));
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			http.post(endpoints.queryAuditLogs.getUrl(), () => {
				auditRequests();
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=2');

		await expect
			.element(screen.getByText('The selected process definition or tenant could not be found'))
			.toBeVisible();
		expect(lookupRequests).toEqual([
			expect.objectContaining({filter: {processDefinitionId: {$eq: 'invoice'}, version: 2}}),
			expect.objectContaining({
				filter: {processDefinitionId: {$eq: 'invoice'}, version: 2},
				page: expect.objectContaining({after: 'second-tenant'}),
			}),
		]);
		expect(auditRequests).not.toHaveBeenCalled();
	});

	it('should scope all versions to the saved tenant without selecting a definition key', async ({worker}) => {
		const auditFilters: unknown[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			http.post(endpoints.queryAuditLogs.getUrl(), async ({request}) => {
				auditFilters.push(queryAuditLogsRequestBodySchema.parse(await request.json()).filter);
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
		await expect.poll(() => auditFilters).toMatchObject([{tenantId: TENANT_B, processDefinitionId: 'invoice'}]);
		expect(auditFilters[0]).not.toHaveProperty('processDefinitionKey');
	});

	it('should safely query all versions with an explicit tenant even when definition lookup fails', async ({worker}) => {
		const auditFilters: unknown[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				return Object.keys(body.filter ?? {}).length === 0 || body.filter?.isLatestVersion
					? HttpResponse.json(createQueryProcessDefinitionsResponse())
					: new HttpResponse(null, {status: 500});
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			http.post(endpoints.queryAuditLogs.getUrl(), async ({request}) => {
				auditFilters.push(queryAuditLogsRequestBodySchema.parse(await request.json()).filter);
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
		await expect.element(screen.getByText("Couldn't load process definitions")).toBeVisible();
		await expect.poll(() => auditFilters).toMatchObject([{tenantId: TENANT_B, processDefinitionId: 'invoice'}]);
		expect(auditFilters[0]).not.toHaveProperty('processDefinitionKey');
	});

	it('should query all versions without tenant inference in a single-tenant deployment', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		const auditFilters: unknown[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				return HttpResponse.json(
					createQueryProcessDefinitionsResponse({items: body.filter?.processDefinitionId ? [] : [A_VERSION_1]}),
				);
			}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			http.post(endpoints.queryAuditLogs.getUrl(), async ({request}) => {
				auditFilters.push(queryAuditLogsRequestBodySchema.parse(await request.json()).filter);
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=all');

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
		await expect.poll(() => auditFilters).toMatchObject([{processDefinitionId: 'invoice'}]);
		expect(auditFilters[0]).not.toHaveProperty('tenantId');
		expect(auditFilters[0]).not.toHaveProperty('processDefinitionKey');
	});

	it('should retain a tenant-scoped audit query and show an error when the process picker fails', async ({worker}) => {
		const auditFilters: unknown[] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				return body.filter?.isLatestVersion
					? new HttpResponse(null, {status: 500})
					: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [B_VERSION_2]}));
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			http.post(endpoints.queryAuditLogs.getUrl(), async ({request}) => {
				auditFilters.push(queryAuditLogsRequestBodySchema.parse(await request.json()).filter);
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
		await expect.element(screen.getByText("Couldn't load process definitions")).toBeVisible();
		await expect.poll(() => auditFilters).toMatchObject([{tenantId: TENANT_B, processDefinitionId: 'invoice'}]);
	});

	it('should display an explicitly saved All versions selection on a single-version process', async ({worker}) => {
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]})),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-A%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		expect(operationsLogSearchSchema.parse(screen.router.state.location.search).allVersions).toBe(true);
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('All versions');
	});

	it('should restore tenant, definition and version across navigation history', async ({worker}) => {
		const auditFilters: unknown[] = [];
		const latestFilters: QueryProcessDefinitionsRequestBody['filter'][] = [];
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				if (body.filter?.isLatestVersion) {
					latestFilters.push(body.filter);
				}
				return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]}));
			}),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: TENANT_A, name: 'Tenant A', description: null},
							{tenantId: TENANT_B, name: 'Tenant B', description: null},
						],
					}),
				),
			}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			http.post(endpoints.queryAuditLogs.getUrl(), async ({request}) => {
				auditFilters.push(queryAuditLogsRequestBodySchema.parse(await request.json()).filter);
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice A');
		await expect.poll(() => auditFilters.length).toBe(1);

		await screen.router.navigate({
			to: '/operate/operations-log',
			search: {tenantId: TENANT_B, process: 'invoice', version: 2},
		});
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice B');
		await expect.poll(() => auditFilters.length).toBe(2);
		expect(latestFilters).toEqual([
			{isLatestVersion: true, tenantId: TENANT_A},
			{isLatestVersion: true, tenantId: TENANT_B},
		]);

		screen.router.history.back();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice A');
		expect(operationsLogSearchSchema.parse(screen.router.state.location.search)).toMatchObject({
			tenantId: TENANT_A,
			process: 'invoice',
			version: 1,
		});
		expect(auditFilters.slice(0, 2)).toMatchObject([
			{tenantId: TENANT_A, processDefinitionKey: 'key-A-1'},
			{tenantId: TENANT_B, processDefinitionKey: 'key-B-2'},
		]);

		screen.router.history.forward();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice B');
		expect(operationsLogSearchSchema.parse(screen.router.state.location.search)).toMatchObject({
			tenantId: TENANT_B,
			process: 'invoice',
			version: 2,
		});
	});

	it('should keep the actor field focused across autosubmitted URL updates', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage();
		const actor = screen.getByLabelText('Actor');
		await userEvent.fill(actor, 'operator');
		await expect
			.poll(() => operationsLogSearchSchema.parse(screen.router.state.location.search).actorId)
			.toBe('operator');
		await expect.element(actor).toHaveFocus();
		await userEvent.type(actor, '2');
		await expect
			.poll(() => operationsLogSearchSchema.parse(screen.router.state.location.search).actorId)
			.toBe('operator2');
	});

	it('should clear the selected process and version when the tenant changes', async ({worker}) => {
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: TENANT_A, name: 'Tenant A', description: null},
							{tenantId: TENANT_B, name: 'Tenant B', description: null},
						],
					}),
				),
			}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveValue('Invoice A');

		screen.getByRole('combobox', {name: 'Select a tenant'}).element().focus();
		await userEvent.keyboard('{Space}');
		await userEvent.keyboard('{ArrowDown}{Enter}');

		await expect
			.poll(() => operationsLogSearchSchema.parse(screen.router.state.location.search))
			.toMatchObject({tenantId: TENANT_B});
		expect(operationsLogSearchSchema.parse(screen.router.state.location.search).process).toBeUndefined();
		expect(operationsLogSearchSchema.parse(screen.router.state.location.search).version).toBeUndefined();
	});

	it('should select the latest version when switching processes in the same tenant', async ({worker}) => {
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), () =>
				HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, OTHER_A]})),
			),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');
		const processField = screen.getByRole('combobox', {name: 'Name'});
		await expect.element(processField).toHaveValue('Invoice A');
		await userEvent.fill(processField, 'Shipment');
		await expect.element(screen.getByRole('option', {name: 'Shipment'})).toBeVisible();
		await userEvent.keyboard('{Enter}');
		await expect.element(processField).toHaveValue('Shipment');

		await expect
			.poll(() => operationsLogSearchSchema.parse(screen.router.state.location.search))
			.toMatchObject({
				tenantId: TENANT_A,
				process: 'shipment',
				version: 5,
			});
	});

	it('should not query audit logs while a selected definition is loading or after lookup failure', async ({worker}) => {
		const auditRequests = vi.fn();
		let releaseLookup: () => void = () => {};
		const lookupHeld = new Promise<void>((resolve) => {
			releaseLookup = resolve;
		});
		worker.use(
			http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
				const body = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
				if (body.filter?.isLatestVersion) {
					return HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]}));
				}
				await lookupHeld;
				return new HttpResponse(null, {status: 500});
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			http.post(endpoints.queryAuditLogs.getUrl(), () => {
				auditRequests();
				return EMPTY_AUDIT.clone();
			}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');

		await expect.element(screen.getByText('Loading selected process definition')).toBeVisible();
		expect(auditRequests).not.toHaveBeenCalled();
		releaseLookup();
		await expect.element(screen.getByText("Couldn't load the selected process definition")).toBeVisible();
		expect(auditRequests).not.toHaveBeenCalled();
	});
});
