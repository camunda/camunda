/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {TooltipProvider} from '@camunda/design-system';
import {describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {AdminOperationsLogPage, type AdminOperationsLogPageProps} from './AdminOperationsLogPage';

const DEBOUNCED = {timeout: 3000};

function createAuditLog(overrides: Partial<AuditLog> = {}): AuditLog {
	return {
		auditLogKey: '2251799813685283',
		entityKey: '2251799813685281',
		entityType: 'USER_TASK',
		operationType: 'CREATE',
		batchOperationKey: null,
		batchOperationType: null,
		timestamp: '2024-01-01T10:00:00.000Z',
		actorId: 'demo',
		actorType: 'USER',
		tenantId: '<default>',
		result: 'SUCCESS',
		category: 'ADMIN',
		processDefinitionId: null,
		processDefinitionKey: null,
		processInstanceKey: null,
		rootProcessInstanceKey: null,
		elementInstanceKey: null,
		jobKey: null,
		userTaskKey: '2251799813685281',
		decisionRequirementsId: null,
		decisionRequirementsKey: null,
		decisionDefinitionId: null,
		decisionDefinitionKey: null,
		decisionEvaluationKey: null,
		deploymentKey: null,
		formKey: null,
		resourceKey: null,
		relatedEntityKey: null,
		relatedEntityType: null,
		entityDescription: null,
		agentElementId: null,
		inboundChannelType: null,
		inboundChannelToolName: null,
		...overrides,
	};
}

async function renderPage(overrides: Partial<AdminOperationsLogPageProps> = {}) {
	const onSearchChange = vi.fn();
	const screen = await render(
		<TooltipProvider>
			<AdminOperationsLogPage
				auditLogs={[createAuditLog()]}
				totalItems={1}
				search={{}}
				onSearchChange={onSearchChange}
				{...overrides}
			/>
		</TooltipProvider>,
	);

	return {screen, onSearchChange};
}

describe('<AdminOperationsLogPage />', () => {
	it('should list the audit log columns', async () => {
		// when
		const {screen} = await renderPage();

		// then
		await expect.element(screen.getByRole('cell', {name: 'Create'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'User task'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'User demo'})).toBeVisible();
	});

	it('should show an accessible label for the actor type icon', async () => {
		// given
		const {screen} = await renderPage({auditLogs: [createAuditLog({actorType: 'CLIENT'})]});

		// then
		await expect.element(screen.getByRole('cell', {name: 'Client demo'})).toBeVisible();
	});

	it('should indicate when an operation was performed by an AI agent', async () => {
		// given
		const {screen} = await renderPage({auditLogs: [createAuditLog({agentElementId: 'agent-1'})]});

		// then
		await expect.element(screen.getByRole('cell', {name: 'User AI agent demo'})).toBeVisible();
	});

	it('should render a success status icon for a successful operation', async () => {
		// given
		const {screen} = await renderPage({auditLogs: [createAuditLog({result: 'SUCCESS'})]});

		// then
		await expect.element(screen.getByLabelText('Success')).toBeVisible();
	});

	it('should render a danger status icon for a failed operation', async () => {
		// given
		const {screen} = await renderPage({auditLogs: [createAuditLog({result: 'FAIL'})]});

		// then
		await expect.element(screen.getByLabelText('Fail')).toBeVisible();
	});

	it('should show the error code in the property column for a failed operation', async () => {
		// given
		const log = createAuditLog({result: 'FAIL', entityDescription: 'ERR_VALIDATION'});

		// when
		const {screen} = await renderPage({auditLogs: [log]});

		// then
		await expect.element(screen.getByText('Error code')).toBeVisible();
		await expect.element(screen.getByText('ERR_VALIDATION')).toBeVisible();
	});

	it('should show the owner in the property column for an authorization creation', async () => {
		// given
		const log = createAuditLog({
			entityType: 'AUTHORIZATION',
			operationType: 'CREATE',
			relatedEntityType: 'USER',
			relatedEntityKey: 'demo-user',
		});

		// when
		const {screen} = await renderPage({auditLogs: [log]});

		// then
		await expect.element(screen.getByText('Owner')).toBeVisible();
		await expect.element(screen.getByText('demo-user')).toBeVisible();
	});

	it('should show the assignee in the property column for an assign operation', async () => {
		// given
		const log = createAuditLog({
			entityType: 'USER_TASK',
			operationType: 'ASSIGN',
			relatedEntityType: 'USER',
			relatedEntityKey: 'demo-user',
		});

		// when
		const {screen} = await renderPage({auditLogs: [log]});

		// then
		await expect.element(screen.getByText('Assignee')).toBeVisible();
	});

	it('should show nothing in the property column for an operation with no property to report', async () => {
		// given
		const log = createAuditLog({entityType: 'USER_TASK', operationType: 'CREATE'});

		// when
		const {screen} = await renderPage({auditLogs: [log]});

		// then
		await expect.element(screen.getByRole('cell', {name: '-'}).first()).toBeVisible();
	});

	it('should hide the owner-type and owner-key filters while the entity type is not AUTHORIZATION', async () => {
		// given
		const {screen} = await renderPage();

		// then
		await expect.element(screen.getByLabelText('Owner type')).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Owner ID')).not.toBeInTheDocument();
	});

	it('should select the AUTHORIZATION entity type', async () => {
		// given
		const {screen, onSearchChange} = await renderPage();

		// when
		await userEvent.click(screen.getByLabelText('Entity type'));
		await userEvent.click(screen.getByRole('option', {name: 'Authorization'}));

		// then
		expect(onSearchChange).toHaveBeenCalledWith({
			entityType: 'AUTHORIZATION',
			relatedEntityType: undefined,
			relatedEntityKey: undefined,
			page: undefined,
		});
	});

	it('should reveal the owner filters when the entity type is already AUTHORIZATION', async () => {
		// given
		const {screen} = await renderPage({search: {entityType: 'AUTHORIZATION'}});

		// then
		await expect.element(screen.getByLabelText('Owner type')).toBeVisible();
		await expect.element(screen.getByLabelText('Owner ID')).toBeVisible();
	});

	it('should clear the owner filters when the entity type changes away from AUTHORIZATION', async () => {
		// given
		const {screen, onSearchChange} = await renderPage({
			search: {entityType: 'AUTHORIZATION', relatedEntityType: 'USER', relatedEntityKey: 'demo'},
		});

		// when
		await userEvent.click(screen.getByLabelText('Entity type'));
		await userEvent.click(screen.getByRole('option', {name: 'Role'}));

		// then
		expect(onSearchChange).toHaveBeenCalledWith({
			entityType: 'ROLE',
			relatedEntityType: undefined,
			relatedEntityKey: undefined,
			page: undefined,
		});
	});

	it('should filter by actor once the reader stops typing', async () => {
		// given
		const {screen, onSearchChange} = await renderPage();

		// when
		await userEvent.fill(screen.getByLabelText('Actor'), 'demo');

		// then
		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({actor: 'demo', page: undefined}), DEBOUNCED);
	});

	it('should filter by owner key once the reader stops typing', async () => {
		// given
		const {screen, onSearchChange} = await renderPage({search: {entityType: 'AUTHORIZATION'}});

		// when
		await userEvent.fill(screen.getByLabelText('Owner ID'), 'demo-user');

		// then
		await vi.waitFor(
			() => expect(onSearchChange).toHaveBeenCalledWith({relatedEntityKey: 'demo-user', page: undefined}),
			DEBOUNCED,
		);
	});

	it('should reverse the sort order when the reader sorts a column', async () => {
		// given
		const {screen, onSearchChange} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Actor'}));

		// then
		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'actorId', sortOrder: 'asc', page: undefined});
	});

	it('should return to the first page when the page size changes', async () => {
		// given
		const {screen, onSearchChange} = await renderPage({search: {page: 3}, totalItems: 200});

		// when
		await userEvent.click(screen.getByRole('combobox').last());
		await userEvent.click(screen.getByRole('option', {name: '100'}));

		// then
		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 100, page: undefined});
	});

	it('should disable the reset control while no filter is active', async () => {
		// given
		const {screen} = await renderPage();

		// then
		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
	});

	it('should enable the reset control once a filter is active and clear it on click', async () => {
		// given
		const {screen, onSearchChange} = await renderPage({search: {actor: 'demo'}});

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		// then
		expect(onSearchChange).toHaveBeenCalledWith({
			operationType: undefined,
			entityType: undefined,
			relatedEntityType: undefined,
			relatedEntityKey: undefined,
			result: undefined,
			actor: undefined,
			timestampFrom: undefined,
			timestampTo: undefined,
			page: undefined,
		});
	});
});
