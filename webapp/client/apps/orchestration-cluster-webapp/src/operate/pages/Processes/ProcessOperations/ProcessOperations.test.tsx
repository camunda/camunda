/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {
	deleteResourceRequestBodySchema,
	queryProcessInstancesRequestBodySchema,
	type ProcessDefinition,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockDeleteResourceEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessOperations} from './ProcessOperations';

const DEFINITION = createProcessDefinition({
	name: 'Order Process',
	processDefinitionId: 'order-process',
	processDefinitionKey: '2251799813685279',
	version: 2,
});
const DELETED_DEFINITION = {...DEFINITION, state: 'DELETED'} satisfies ProcessDefinition;
const ACTION_NAME = 'Delete Process Definition "Order Process - Version 2"';
const HISTORY_ACTION_NAME = 'Delete Process Definition History "Order Process - Version 2"';
const RUNNING_INSTANCES_TITLE = 'Only process definitions without running instances can be deleted.';
const CONFIRMATION = 'Yes, I confirm I want to delete this process definition.';
const HISTORY_CONFIRMATION = 'Yes, I confirm I want to permanently delete this process definition history.';

function mockDrainingDefinitions(items: ProcessDefinition[] = []) {
	return mockQueryProcessDefinitionsEndpoint({
		successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items})),
	});
}

function mockRunningInstancesCount(totalItems: number) {
	return mockQueryProcessInstancesEndpoint({
		schema: queryProcessInstancesRequestBodySchema.refine(
			({filter, page}) =>
				JSON.stringify(filter) ===
					JSON.stringify({
						processDefinitionKey: {$eq: DEFINITION.processDefinitionKey},
						$or: [{state: {$eq: 'ACTIVE'}}, {hasIncident: true}],
					}) && page?.limit === 0,
		),
		successResponse: HttpResponse.json(createQueryProcessInstancesResponse({page: {totalItems}})),
		failureResponse: new HttpResponse(null, {status: 400}),
	});
}

function renderOperations(definition: ProcessDefinition = DEFINITION) {
	return renderWithRouter(
		() => (
			<>
				<ProcessOperations definition={definition} />
				<Notifications />
			</>
		),
		{path: '/operate/processes', initialEntry: '/operate/processes?process=order-process&version=2'},
	);
}

async function confirmDeletion(screen: Awaited<ReturnType<typeof renderOperations>>, actionName = ACTION_NAME) {
	await userEvent.click(screen.getByRole('button', {name: actionName}));
	await userEvent.click(screen.getByText(actionName === ACTION_NAME ? CONFIRMATION : HISTORY_CONFIRMATION));
	await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
}

describe('<ProcessOperations />', () => {
	afterEach(() => notificationsStore.reset());

	it('should offer deletion of a definition without running instances', async ({worker}) => {
		worker.use(mockDrainingDefinitions(), mockRunningInstancesCount(0));

		const screen = await renderOperations();

		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeEnabled();
		await expect.element(screen.getByText('Deleted', {exact: true})).not.toBeInTheDocument();
	});

	it('should disable deletion while the definition has running instances', async ({worker}) => {
		worker.use(mockDrainingDefinitions(), mockRunningInstancesCount(3));

		const screen = await renderOperations();

		await expect.element(screen.getByRole('button', {name: RUNNING_INSTANCES_TITLE})).toBeDisabled();
	});

	it('should keep deletion enabled while running instances are unknown', async ({worker}) => {
		worker.use(
			mockDrainingDefinitions(),
			mockQueryProcessInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
		);

		const screen = await renderOperations();

		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeEnabled();
	});

	it.for([
		{source: 'the draining lookup', definition: DEFINITION, drainingDefinitions: [{...DEFINITION, state: 'DRAINING'}]},
		{source: 'the definition state', definition: {...DEFINITION, state: 'DRAINING'}, drainingDefinitions: []},
	] as const)(
		'should replace deletion with the draining tag when $source reports draining',
		async ({definition, drainingDefinitions}, {worker}) => {
			worker.use(mockDrainingDefinitions([...drainingDefinitions]), mockRunningInstancesCount(0));

			const screen = await renderOperations(definition);

			await expect.element(screen.getByText('Draining', {exact: true})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: ACTION_NAME})).not.toBeInTheDocument();
		},
	);

	it('should offer history deletion for a deleted definition still listed as draining', async ({worker}) => {
		worker.use(mockDrainingDefinitions([{...DEFINITION, state: 'DRAINING'}]), mockRunningInstancesCount(0));

		const screen = await renderWithRouter(
			() => (
				<>
					<section aria-label="Listed definition">
						<ProcessOperations definition={DEFINITION} />
					</section>
					<section aria-label="Deleted definition">
						<ProcessOperations definition={DELETED_DEFINITION} />
					</section>
				</>
			),
			{path: '/operate/processes'},
		);

		await expect
			.element(screen.getByRole('region', {name: 'Listed definition'}).getByText('Draining', {exact: true}))
			.toBeVisible();
		const deleted = screen.getByRole('region', {name: 'Deleted definition'});
		await expect.element(deleted.getByRole('button', {name: HISTORY_ACTION_NAME})).toBeEnabled();
		await expect.element(deleted.getByText('Draining', {exact: true})).not.toBeInTheDocument();
	});

	it('should explain the deletion impact and require fresh confirmation after cancel', async ({worker}) => {
		worker.use(mockDrainingDefinitions(), mockRunningInstancesCount(0));
		const screen = await renderOperations();

		await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));

		const dialog = screen.getByRole('dialog');
		await expect.element(dialog.getByRole('heading', {name: 'Delete Process Definition', exact: true})).toBeVisible();
		await expect.element(dialog.getByText('You are about to delete the following process definition:')).toBeVisible();
		await expect.element(dialog.getByRole('cell', {name: 'Order Process - Version 2'})).toBeVisible();
		await expect
			.element(
				dialog.getByText('Deleting a process definition will permanently remove it and will impact the following:'),
			)
			.toBeVisible();
		await expect
			.element(
				dialog.getByRole('link', {
					name: 'For a detailed overview, please view our guide on deleting a process definition',
				}),
			)
			.toHaveAttribute('href', 'https://docs.camunda.io/docs/components/operate/userguide/delete-resources/');
		await expect.element(dialog.getByRole('button', {name: 'Delete', exact: true})).toBeDisabled();

		await userEvent.click(dialog.getByText(CONFIRMATION));
		await expect.element(dialog.getByRole('button', {name: 'Delete', exact: true})).toBeEnabled();
		await userEvent.click(dialog.getByRole('button', {name: 'Cancel', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));

		await expect.element(screen.getByRole('checkbox', {name: CONFIRMATION})).not.toBeChecked();
	});

	it.for([
		{change: 'becomes deleted', nextDefinition: DELETED_DEFINITION, confirmation: HISTORY_CONFIRMATION},
		{
			change: 'is replaced by another version',
			nextDefinition: {...DEFINITION, processDefinitionKey: '2251799813685299', version: 3},
			confirmation: CONFIRMATION,
		},
	])(
		'should require a new confirmation when the definition $change while confirming',
		async ({nextDefinition, confirmation}, {worker}) => {
			worker.use(mockDrainingDefinitions(), mockRunningInstancesCount(0));
			let changeDefinition = () => {};
			const OperationsWithChangingDefinition = () => {
				const [definition, setDefinition] = useState(DEFINITION);
				changeDefinition = () => setDefinition(nextDefinition);
				return <ProcessOperations definition={definition} />;
			};
			const screen = await renderWithRouter(OperationsWithChangingDefinition, {path: '/operate/processes'});

			await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));
			await userEvent.click(screen.getByText(CONFIRMATION));
			await expect.element(screen.getByRole('button', {name: 'Delete', exact: true})).toBeEnabled();
			changeDefinition();

			const dialog = screen.getByRole('dialog');
			await expect.element(dialog.getByRole('checkbox', {name: confirmation})).not.toBeChecked();
			await expect.element(dialog.getByRole('button', {name: 'Delete', exact: true})).toBeDisabled();
		},
	);

	it('should mark a deleted definition and offer permanent history deletion', async ({worker}) => {
		worker.use(mockDrainingDefinitions(), mockRunningInstancesCount(0));
		const screen = await renderOperations(DELETED_DEFINITION);

		await expect.element(screen.getByText('Deleted', {exact: true})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: HISTORY_ACTION_NAME}));

		const dialog = screen.getByRole('dialog');
		await expect
			.element(
				dialog.getByText(
					'This process definition is already deleted. Continuing will permanently remove its remaining history:',
				),
			)
			.toBeVisible();
		await expect
			.element(
				dialog.getByText(
					'Deleting the remaining process definition history is permanent and will impact the following:',
				),
			)
			.toBeVisible();
		await expect.element(dialog.getByRole('checkbox', {name: HISTORY_CONFIRMATION})).not.toBeChecked();
	});

	it.for([
		{name: 'definition', definition: DEFINITION, actionName: ACTION_NAME},
		{name: 'history', definition: DELETED_DEFINITION, actionName: HISTORY_ACTION_NAME},
	])(
		'should disable $name deletion while pending and notify once created',
		async ({definition, actionName}, {worker}) => {
			worker.use(
				mockDrainingDefinitions(),
				mockRunningInstancesCount(0),
				mockDeleteResourceEndpoint({
					schema: deleteResourceRequestBodySchema.refine((body) => body?.deleteHistory === true),
					successResponse: HttpResponse.json({resourceKey: DEFINITION.processDefinitionKey, batchOperation: null}),
					failureResponse: new HttpResponse(null, {status: 400}),
					delay: 500,
				}),
			);
			const screen = await renderOperations(definition);

			await confirmDeletion(screen, actionName);

			await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: actionName})).toBeDisabled();
			await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: actionName})).toBeEnabled();
		},
	);

	it.for([
		{
			name: 'a forbidden response',
			response: new HttpResponse(null, {status: 403}),
			title: "You don't have permission to perform this operation",
			subtitle: 'Contact the administrator if you need access.',
		},
		{
			name: 'a server error',
			response: new HttpResponse(null, {status: 500}),
			title: "Couldn't create operation",
			subtitle: undefined,
		},
	])('should notify about $name and allow another attempt', async ({response, title, subtitle}, {worker}) => {
		worker.use(
			mockDrainingDefinitions(),
			mockRunningInstancesCount(0),
			mockDeleteResourceEndpoint({successResponse: response}),
		);
		const screen = await renderOperations();

		await confirmDeletion(screen);

		await expect.element(screen.getByText(title, {exact: true})).toBeVisible();
		if (subtitle !== undefined) {
			await expect.element(screen.getByText(subtitle, {exact: true})).toBeVisible();
		}
		await expect.element(screen.getByText('Operation created', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeEnabled();

		worker.use(
			mockDeleteResourceEndpoint({
				successResponse: HttpResponse.json({resourceKey: DEFINITION.processDefinitionKey, batchOperation: null}),
			}),
		);
		await confirmDeletion(screen);

		await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
	});
});
