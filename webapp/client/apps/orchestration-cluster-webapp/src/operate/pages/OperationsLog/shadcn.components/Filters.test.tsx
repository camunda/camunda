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
import {useSearch} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionEndpoint,
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
import {OperationsLog} from './OperationsLog';
import {operationsLogSearchSchema} from '../operationsLog.schema';

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
const AUDIT_WITH_ROW = HttpResponse.json(
	createQueryAuditLogsResponse({items: [createAuditLog({actorId: 'unscoped-actor'})]}),
);
const REJECTED_AUDIT_QUERY = new HttpResponse(null, {status: 400});
const NO_DECISION_DEFINITIONS = HttpResponse.json(createQueryDecisionDefinitionsResponse());
const DEFINITION_UNAVAILABLE = 'The selected process definition or tenant could not be found';
const TWO_TENANTS_USER = HttpResponse.json(
	createCurrentUser({
		tenants: [
			{tenantId: TENANT_A, name: 'Tenant A', description: null},
			{tenantId: TENANT_B, name: 'Tenant B', description: null},
		],
	}),
);

function mockAuditLogsFilteredBy(filter: z.ZodType) {
	return mockQueryAuditLogsEndpoint({
		schema: z.object({filter}),
		successResponse: EMPTY_AUDIT,
		failureResponse: REJECTED_AUDIT_QUERY,
	});
}

function OperationsLogFromUrl() {
	const search = useSearch({strict: false});
	return (
		<div className="h-screen">
			<OperationsLog {...operationsLogSearchSchema.parse(search)} />
		</div>
	);
}

function renderPage(query = '') {
	return renderWithRouter(OperationsLogFromUrl, {
		path: '/operate-preview/operations-log',
		initialEntry: `/operate-preview/operations-log${query}`,
	});
}

describe('Operations Log saved filters', () => {
	beforeEach(() => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {
						isMultiTenancyEnabled: true,
						isTenantsApiEnabled: true,
						isWaitStatesEnabled: true,
						maxRequestSize: 0,
					},
				}),
			),
		);
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it.for([
		{query: '?version=2', emptyState: 'No operations match these filters'},
		{query: '?allVersions=true', emptyState: 'No operation log items yet'},
	])('should show the log unfiltered by process for orphaned $query', async ({query, emptyState}, {worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(
				z.object({processDefinitionId: z.undefined().optional(), processDefinitionKey: z.undefined().optional()}),
			),
		);

		const screen = await renderPage(query);

		await expect.element(screen.getByText(emptyState)).toBeVisible();
	});

	it('should resolve a legacy link in the selected tenant beyond the first page', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				schema: z.strictObject({
					filter: z.union([
						z.strictObject({isLatestVersion: z.literal(true), tenantId: z.literal(TENANT_B)}),
						z.strictObject({
							processDefinitionId: z.strictObject({$eq: z.literal('invoice')}),
							tenantId: z.literal(TENANT_B),
						}),
						z.strictObject({
							processDefinitionId: z.strictObject({$eq: z.literal('invoice')}),
							tenantId: z.literal(TENANT_B),
							version: z.literal(2),
						}),
					]),
					page: z.strictObject({limit: z.literal(1000), after: z.literal('page-2')}),
				}),
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [B_VERSION_2]})),
				failureResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [],
						page: {totalItems: 1, hasMoreTotalItems: false, endCursor: 'page-2'},
					}),
				),
			}),
			mockCurrentUserEndpoint({successResponse: TWO_TENANTS_USER}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(
				z.object({
					processDefinitionKey: z.literal('key-B-2'),
					tenantId: z.literal(TENANT_B),
					operationType: z.object({$in: z.tuple([z.literal('CREATE'), z.literal('UPDATE')])}),
					entityType: z.object({$in: z.tuple([z.literal('JOB'), z.literal('VARIABLE')])}),
				}),
			),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=2&operationType=CREATE,UPDATE&entityType=JOB,VARIABLE',
		);

		await expect.element(screen.getByText('No operations match these filters')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveTextContent('Invoice B');
		await expect.element(screen.getByRole('combobox', {name: 'Select a tenant'})).toMatchTextContent('Tenant B');
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toBeEnabled();
	});

	it('should resolve historical process names by key without loading every definition', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [B_VERSION_2]})),
			}),
			mockGetProcessDefinitionEndpoint({
				successResponse: HttpResponse.json(
					createGetProcessDefinitionResponse({
						...B_VERSION_2,
						processDefinitionKey: 'key-B-1',
						version: 1,
						name: 'Historic Invoice',
					}),
				),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
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
	});

	it('should retain a process ID when its definition name cannot be loaded', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionEndpoint({successResponse: new HttpResponse(null, {status: 503})}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
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

		await expect.element(screen.getByText('invoice')).toBeVisible();
	});

	it('should require a tenant before offering processes in a multi-tenant deployment', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('No operation log items yet')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toBeDisabled();
	});

	it('should load cross-tenant process options only when All tenants is explicitly selected', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=all');

		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toBeEnabled();
		await userEvent.click(screen.getByLabelText('Name'));
		await expect.element(screen.getByRole('option', {name: /Invoice A/})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: /Invoice B/})).toBeVisible();
	});

	it('should offer only versions belonging to the selected tenant', async ({worker}) => {
		const B_VERSION_4 = createProcessDefinition({...B_VERSION_2, processDefinitionKey: 'key-B-4', version: 4});
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2, B_VERSION_4]}),
				),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-B%3E&process=invoice&version=2');
		const versionField = screen.getByRole('combobox', {name: 'Version'});
		await expect.element(versionField).toBeEnabled();
		await userEvent.click(versionField);

		await expect.element(screen.getByRole('option', {name: '4'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: '1'})).not.toBeInTheDocument();
	});

	it('should not offer All versions for a single-version definition unless explicitly selected', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');
		const versionField = screen.getByRole('combobox', {name: 'Version'});
		await expect.element(versionField).toBeEnabled();
		await userEvent.click(versionField);

		await expect.element(screen.getByRole('option', {name: 'All versions'})).not.toBeInTheDocument();
	});

	it('should show no audit rows when the selected version is missing', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryAuditLogsEndpoint({successResponse: AUDIT_WITH_ROW}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&processDefinitionId=invoice&processDefinitionVersion=42');

		await expect.element(screen.getByText(DEFINITION_UNAVAILABLE)).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveTextContent('Invoice A');
		await expect.element(screen.getByText('unscoped-actor')).not.toBeInTheDocument();
	});

	it('should let the user dismiss the unavailable definition alert', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryAuditLogsEndpoint({successResponse: AUDIT_WITH_ROW}),
		);
		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&processDefinitionId=invoice&processDefinitionVersion=42');
		await expect.element(screen.getByText(DEFINITION_UNAVAILABLE)).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Dismiss'}));

		await expect.element(screen.getByText(DEFINITION_UNAVAILABLE)).not.toBeInTheDocument();
	});

	it('should show the timestamp range in the picker and drop it from the query when cleared', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(
				z.object({
					timestamp: z.strictObject({
						$gt: z.literal(new Date('2024-06-10T12:00:00').toISOString()),
						$lt: z.literal(new Date('2024-06-20T18:30:00').toISOString()),
					}),
				}),
			),
		);

		const screen = await renderPage('?timestampAfter=2024-06-10T12:00:00&timestampBefore=2024-06-20T18:30:00');
		const picker = screen.getByRole('button', {name: /Timestamp date range/});
		await expect.element(picker).toHaveTextContent('Jun 10, 2024 12:00:00 PM – Jun 20, 2024 06:30:00 PM');
		await expect.element(screen.getByText('No operations match these filters')).toBeVisible();

		worker.use(
			mockQueryAuditLogsEndpoint({
				schema: z.object({filter: z.object({timestamp: z.never().optional()})}),
				successResponse: AUDIT_WITH_ROW,
				failureResponse: REJECTED_AUDIT_QUERY,
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Clear date and time range'}));

		await expect.element(picker).toHaveTextContent('Enter date range');
		await expect.element(screen.getByText('unscoped-actor')).toBeVisible();
	});

	it('should block a selected version when process resolution fails even if decision names fail', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 503})}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 503})}),
			mockQueryAuditLogsEndpoint({successResponse: AUDIT_WITH_ROW}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');

		await expect.element(screen.getByText("Couldn't load the selected process definition")).toBeVisible();
		await expect.element(screen.getByRole('table', {name: 'Operations log'})).not.toBeInTheDocument();
		await expect.element(screen.getByText('unscoped-actor')).not.toBeInTheDocument();
	});

	it('should list logs across tenants for an ambiguous tenantless selection', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(
				z.object({
					processDefinitionId: z.literal('invoice'),
					tenantId: z.undefined().optional(),
					processDefinitionKey: z.undefined().optional(),
				}),
			),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=all');

		await expect.element(screen.getByText('No operations match these filters')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveTextContent('invoice');
	});

	it('should resolve a tenantless saved version when only one tenant has that version', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(z.object({tenantId: z.literal(TENANT_B), processDefinitionKey: z.literal('key-B-2')})),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=2');

		await expect.element(screen.getByText('No operations match these filters')).toBeVisible();
	});

	it('should not pick a tenant when the requested version exists in multiple tenants', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({...A_VERSION_1, version: 2}), B_VERSION_2],
					}),
				),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryAuditLogsEndpoint({successResponse: AUDIT_WITH_ROW}),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=2');

		await expect.element(screen.getByText(DEFINITION_UNAVAILABLE)).toBeVisible();
		await expect.element(screen.getByText('unscoped-actor')).not.toBeInTheDocument();
	});

	it('should scope all versions to the saved tenant without selecting a definition key', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(
				z.object({
					tenantId: z.literal(TENANT_B),
					processDefinitionId: z.literal('invoice'),
					processDefinitionKey: z.undefined().optional(),
				}),
			),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		await expect.element(screen.getByText('No operations match these filters')).toBeVisible();
	});

	it('should retain a tenant-scoped audit query and show an error when the process picker fails', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(z.object({tenantId: z.literal(TENANT_B), processDefinitionId: z.literal('invoice')})),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-B%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		await expect.element(screen.getByText('No operations match these filters')).toBeVisible();
		await expect.element(screen.getByText("Couldn't load process definitions")).toHaveLength(2);
	});

	it('should query all versions without tenant inference in a single-tenant deployment', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockAuditLogsFilteredBy(
				z.object({
					processDefinitionId: z.literal('invoice'),
					tenantId: z.undefined().optional(),
					processDefinitionKey: z.undefined().optional(),
				}),
			),
		);

		const screen = await renderPage('?processDefinitionId=invoice&processDefinitionVersion=all');

		await expect.element(screen.getByText('No operations match these filters')).toBeVisible();
	});

	it('should display an explicitly saved All versions selection on a single-version process', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage(
			'?tenantId=%3Ctenant-A%3E&processDefinitionId=invoice&processDefinitionVersion=all',
		);

		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toMatchTextContent('All versions');
	});

	it('should keep the actor field focused while typing triggers filtered results', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				schema: z.object({filter: z.object({actorId: z.string().startsWith('operator')})}),
				successResponse: AUDIT_WITH_ROW,
				failureResponse: EMPTY_AUDIT,
			}),
		);

		const screen = await renderPage();
		const actor = screen.getByLabelText('Actor');
		await userEvent.fill(actor, 'operator');

		await expect.element(screen.getByText('unscoped-actor')).toBeVisible();
		await expect.element(actor).toHaveFocus();
		await userEvent.type(actor, '2');
		await expect.element(actor).toHaveValue('operator2');
		await expect.element(actor).toHaveFocus();
	});

	it('should clear the selected process and version when the tenant changes', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, B_VERSION_2]})),
			}),
			mockCurrentUserEndpoint({successResponse: TWO_TENANTS_USER}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveTextContent('Invoice A');

		await userEvent.click(screen.getByRole('combobox', {name: 'Select a tenant'}));
		await userEvent.click(screen.getByRole('option', {name: 'Tenant B'}));

		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toHaveTextContent('Search by process name');
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toBeDisabled();
	});

	it('should select the latest version when switching processes in the same tenant', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1, OTHER_A]})),
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: EMPTY_AUDIT}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');
		const processField = screen.getByRole('combobox', {name: 'Name'});
		await expect.element(processField).toHaveTextContent('Invoice A');
		await userEvent.click(screen.getByLabelText('Name'));
		await userEvent.click(screen.getByRole('option', {name: 'Shipment'}));

		await expect.element(processField).toHaveTextContent('Shipment');
		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toHaveTextContent('5');
	});

	it('should not show audit rows while a selected definition is loading', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [A_VERSION_1]})),
				delay: 'infinite',
			}),
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockQueryAuditLogsEndpoint({successResponse: AUDIT_WITH_ROW}),
		);

		const screen = await renderPage('?tenantId=%3Ctenant-A%3E&process=invoice&version=1');

		await expect.element(screen.getByText('Loading selected process definition')).toBeVisible();
		await expect.element(screen.getByText('unscoped-actor')).not.toBeInTheDocument();
	});
});
