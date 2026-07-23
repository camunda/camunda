/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockQueryAuditLogsEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import type {OperationsLogSearch} from './operationsLog.schema';
import {OperationsLog} from './OperationsLog';

const PROCESS_DEFINITIONS = HttpResponse.json(
	createQueryProcessDefinitionsResponse({
		items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 1})],
	}),
);

const NO_DECISION_DEFINITIONS = HttpResponse.json(createQueryDecisionDefinitionsResponse());

function renderPage(search?: Partial<OperationsLogSearch>) {
	return renderWithRouter(() => <OperationsLog {...(search ?? {})} />, {path: '/operate/operations-log'});
}

describe('<OperationsLog />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the filters panel and the instances table header', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('Process', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('Operations Log')).toBeVisible();
	});

	it('should render all table column headers', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('columnheader', {name: /operation type/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /entity type/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /entity key/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /parent entity/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /details/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /actor/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /date/i})).toBeVisible();
	});

	it('should render the empty state without filters', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('No operation log items yet')).toBeVisible();
	});

	it('should render the empty state with a filter applied', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
		);

		const screen = await renderPage({actorId: 'demo'});

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
	});

	it('should render audit log rows with humanized operation and entity type labels', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								auditLogKey: '123',
								operationType: 'UPDATE',
								entityType: 'VARIABLE',
								result: 'SUCCESS',
								actorId: 'user1',
								actorType: 'ANONYMOUS',
								timestamp: '2024-01-01T12:30:45.000Z',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('Update')).toBeVisible();
		await expect.element(screen.getByText('Variable')).toBeVisible();
		await expect.element(screen.getByText('user1')).toBeVisible();
		await expect
			.element(screen.getByText(format(parseISO('2024-01-01T12:30:45.000Z'), 'yyyy-MM-dd HH:mm:ss')))
			.toBeVisible();
	});

	it('should render a batch operation link for BATCH entity types', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								auditLogKey: '789',
								entityType: 'BATCH',
								operationType: 'CANCEL',
								batchOperationKey: 'batch-123',
								batchOperationType: 'CANCEL_PROCESS_INSTANCE',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect
			.element(screen.getByRole('link', {name: 'View batch operation batch-123'}))
			.toHaveAttribute('href', '/operate/batch-operations/batch-123');
	});

	it('should render a process instance link for PROCESS_INSTANCE entity types', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								auditLogKey: '123',
								entityKey: '999',
								processInstanceKey: '999',
								entityType: 'PROCESS_INSTANCE',
								operationType: 'CANCEL',
								processDefinitionKey: '2251799813685279',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect
			.element(screen.getByRole('link', {name: 'View process instance 999'}))
			.toHaveAttribute('href', '/operate/processes/999');
	});

	it('should render a decision instance link for DECISION entity types', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [createDecisionDefinition({decisionDefinitionKey: '888', name: 'My Decision'})],
					}),
				),
			}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								auditLogKey: '123',
								entityKey: '888',
								entityType: 'DECISION',
								operationType: 'EVALUATE',
								decisionDefinitionKey: '888',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect
			.element(screen.getByRole('link', {name: 'View decision instance 888'}))
			.toHaveAttribute('href', '/operate/decisions/888');
	});

	it('should open the details modal when the comment button is clicked', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({items: [createAuditLog({auditLogKey: '123'})]}),
				),
			}),
		);

		const screen = await renderPage();

		await screen.getByRole('button', {name: /open details/i}).click();

		await expect.element(screen.getByRole('dialog')).toBeVisible();
	});
});
