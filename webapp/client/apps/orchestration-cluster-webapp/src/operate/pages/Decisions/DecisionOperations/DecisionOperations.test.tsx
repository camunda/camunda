/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {deleteResourceRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockDeleteResourceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createDecisionDefinition} from '#/shared-test-modules/api-mocks/decision-definitions';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {DecisionOperations} from './DecisionOperations';

const DEFINITION = createDecisionDefinition({tenantId: 'tenant-a'});
const INITIAL_ENTRY =
	'/operate/decisions?decisionDefinitionId=my-decision&decisionDefinitionVersion=1&tenantId=tenant-a&businessId=order-1&failed=false';
const ACTION_NAME = 'Delete Decision Definition "My Decision - Version 1"';
const CONFIRMATION = 'Yes, I confirm I want to delete this DRD and all related instances.';

function renderOperations(definition = DEFINITION) {
	return renderWithRouter(
		() => (
			<>
				<DecisionOperations definition={definition} />
				<Notifications />
			</>
		),
		{path: '/operate/decisions', initialEntry: INITIAL_ENTRY},
	);
}

async function submit(screen: Awaited<ReturnType<typeof renderOperations>>) {
	await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));
	await userEvent.click(screen.getByText(CONFIRMATION));
	await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
}

describe('<DecisionOperations />', () => {
	afterEach(() => notificationsStore.reset());

	it('should explain DRD-wide deletion and require fresh confirmation after cancel', async () => {
		const screen = await renderOperations();
		await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));

		await expect.element(screen.getByRole('heading', {name: 'Delete DRD', exact: true})).toBeVisible();
		await expect.element(screen.getByText('You are about to delete the following DRD:')).toBeVisible();
		await expect.element(screen.getByText('My DRD', {exact: true})).toBeVisible();
		await expect.element(screen.getByText(/All other decision tables and literal expressions/)).toBeVisible();
		await expect.element(screen.getByText(/could result in process incidents/)).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: 'Read more about deleting a decision definition'}))
			.toBeVisible();

		await userEvent.click(screen.getByText(CONFIRMATION));
		await userEvent.click(screen.getByRole('button', {name: 'Cancel', exact: true}));
		await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));

		await expect.element(screen.getByRole('checkbox', {name: CONFIRMATION})).not.toBeChecked();
		expect(screen.router.state.location.href).toBe(INITIAL_ENTRY);
	});

	it('should use the DRD ID when its name is unavailable', async () => {
		const screen = await renderOperations(createDecisionDefinition({decisionRequirementsName: null}));
		await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));
		await expect.element(screen.getByText('my-drd', {exact: true})).toBeVisible();
	});

	it('should block further deletion while pending, then notify and clear only the deleted selection', async ({
		worker,
	}) => {
		worker.use(
			mockDeleteResourceEndpoint({
				schema: deleteResourceRequestBodySchema.refine((body) => body?.deleteHistory === true),
				successResponse: HttpResponse.json({resourceKey: DEFINITION.decisionRequirementsKey, batchOperation: null}),
				failureResponse: new HttpResponse(null, {status: 400}),
				delay: 500,
			}),
		);
		const screen = await renderOperations();

		await submit(screen);

		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeDisabled();
		await expect.element(screen.getByRole('heading', {name: 'Delete DRD', exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeEnabled();
		expect(screen.router.state.location.search).toEqual({tenantId: 'tenant-a', businessId: 'order-1', failed: false});
	});

	it.for([
		{
			name: 'a forbidden response',
			response: new HttpResponse(null, {status: 403}),
			title: "You don't have permission to perform this operation",
			subtitle: 'Please contact the administrator if you need access.',
		},
		{
			name: 'a server error',
			response: new HttpResponse(null, {status: 500}),
			title: 'Operation could not be created',
			subtitle: undefined,
		},
		{
			name: 'a network error',
			response: HttpResponse.error(),
			title: 'Operation could not be created',
			subtitle: undefined,
		},
	])('should recover from $name without changing the selection', async ({response, title, subtitle}, {worker}) => {
		worker.use(mockDeleteResourceEndpoint({successResponse: response}));
		const screen = await renderOperations();
		await submit(screen);

		await expect.element(screen.getByText(title, {exact: true})).toBeVisible();
		if (subtitle !== undefined) {
			await expect.element(screen.getByText(subtitle)).toBeVisible();
		}
		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeEnabled();
		expect(screen.router.state.location.href).toBe(INITIAL_ENTRY);

		worker.use(mockDeleteResourceEndpoint({successResponse: HttpResponse.json({})}));
		await submit(screen);
		await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
	});

	it.for([{tenantId: 'tenant-b'}, {tenantId: 'all'}, {tenantId: undefined}])(
		'should not clear a newer tenant scope $tenantId when the previous deletion finishes',
		async ({tenantId}, {worker}) => {
			worker.use(mockDeleteResourceEndpoint({successResponse: HttpResponse.json({}), delay: 500}));
			const screen = await renderOperations();
			await submit(screen);
			await screen.router.navigate({
				to: '/operate/decisions',
				search: {decisionDefinitionId: 'my-decision', decisionDefinitionVersion: 1, tenantId},
			});

			await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
			expect(screen.router.state.location.search).toEqual({
				decisionDefinitionId: 'my-decision',
				decisionDefinitionVersion: 1,
				...(tenantId === undefined ? {} : {tenantId}),
			});
		},
	);

	it('should not navigate back to decisions after leaving while deletion is pending', async ({worker}) => {
		worker.use(mockDeleteResourceEndpoint({successResponse: HttpResponse.json({}), delay: 500}));
		const screen = await renderOperations();
		await submit(screen);
		await screen.router.navigate({to: '/operate'});

		await expect
			.poll(() => notificationsStore.notifications.some(({title}) => title === 'Operation created'))
			.toBe(true);
		expect(screen.router.state.location.pathname).toBe('/operate');
	});
});
