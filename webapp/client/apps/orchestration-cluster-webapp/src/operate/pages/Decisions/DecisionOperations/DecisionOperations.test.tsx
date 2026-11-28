/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse, http} from 'msw';
import {useQuery} from '@tanstack/react-query';
import {Link, useSearch} from '@tanstack/react-router';
import {
	deleteResourceRequestBodySchema,
	endpoints,
	queryDecisionDefinitionsRequestBodySchema,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockDeleteResourceEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {decisionDefinitionSelectionOptions} from '../decisions.queries';
import {DecisionOperations} from './DecisionOperations';

const DEFINITION = createDecisionDefinition({tenantId: 'tenant-a'});
const NEXT_VERSION_DEFINITION = createDecisionDefinition({
	decisionDefinitionId: DEFINITION.decisionDefinitionId,
	version: DEFINITION.version + 1,
	tenantId: DEFINITION.tenantId,
	decisionDefinitionKey: '2251799813685290',
	decisionRequirementsKey: '2251799813685291',
});
const SAME_DRD_DEFINITION = createDecisionDefinition({
	name: 'My Other Decision',
	decisionDefinitionId: 'my-other-decision',
	tenantId: DEFINITION.tenantId,
	decisionDefinitionKey: '2251799813685292',
	decisionRequirementsKey: DEFINITION.decisionRequirementsKey,
});
const SAME_DRD_ACTION_NAME = 'Delete Decision Definition "My Other Decision - Version 1"';
const DEFINITIONS_BY_VERSION: Partial<Record<number, typeof DEFINITION>> = {
	[DEFINITION.version]: DEFINITION,
	[NEXT_VERSION_DEFINITION.version]: NEXT_VERSION_DEFINITION,
};
const INITIAL_ENTRY =
	'/operate/decisions?decisionDefinitionId=my-decision&decisionDefinitionVersion=1&tenantId=tenant-a&businessId=order-1&failed=false';
const ACTION_NAME = 'Delete Decision Definition "My Decision - Version 1"';
const NEXT_VERSION_ACTION_NAME = 'Delete Decision Definition "My Decision - Version 2"';
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

function renderOperationsWithVersionLink() {
	return renderWithRouter(
		() => {
			const {decisionDefinitionVersion} = useSearch({strict: false});
			const definition = DEFINITIONS_BY_VERSION[decisionDefinitionVersion ?? DEFINITION.version] ?? DEFINITION;

			return (
				<>
					<Link
						to="/operate/decisions"
						search={(prev) => ({
							...prev,
							evaluated: prev.evaluated ?? false,
							failed: prev.failed ?? false,
							decisionDefinitionVersion: NEXT_VERSION_DEFINITION.version,
						})}
					>
						Version 2
					</Link>
					<DecisionOperations definition={definition} />
					<Notifications />
				</>
			);
		},
		{path: '/operate/decisions', initialEntry: INITIAL_ENTRY},
	);
}

function mockDecisionDefinitionsByIdAndVersion(definitions: (typeof DEFINITION)[]) {
	return http.post(endpoints.queryDecisionDefinitions.getUrl(), async ({request}) => {
		const {filter} = queryDecisionDefinitionsRequestBodySchema.parse(await request.json());
		const items = definitions.filter(
			(definition) =>
				definition.decisionDefinitionId === filter?.decisionDefinitionId && definition.version === filter.version,
		);
		return HttpResponse.json(createQueryDecisionDefinitionsResponse({items}));
	});
}

function renderOperationsWithDecisionLink() {
	return renderWithRouter(
		() => {
			const {decisionDefinitionId, decisionDefinitionVersion, tenantId} = useSearch({strict: false});
			const {data} = useQuery(
				decisionDefinitionSelectionOptions({decisionDefinitionId, decisionDefinitionVersion, tenantId}),
			);
			const definition = data?.items[0];

			return (
				<>
					<Link
						to="/operate/decisions"
						search={(prev) => ({
							...prev,
							evaluated: prev.evaluated ?? false,
							failed: prev.failed ?? false,
							decisionDefinitionId: SAME_DRD_DEFINITION.decisionDefinitionId,
						})}
					>
						My Other Decision
					</Link>
					{definition !== undefined && <DecisionOperations definition={definition} />}
					<Notifications />
				</>
			);
		},
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
		const screen = await renderOperations(createDecisionDefinition({decisionRequirementsName: ''}));
		await userEvent.click(screen.getByRole('button', {name: ACTION_NAME}));
		await expect.element(screen.getByText('my-drd', {exact: true})).toBeVisible();
	});

	it('should block further deletion while pending', async ({worker}) => {
		worker.use(mockDeleteResourceEndpoint({successResponse: HttpResponse.json({}), delay: 'infinite'}));
		const screen = await renderOperations();

		await submit(screen);

		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeDisabled();
		await expect.element(screen.getByRole('heading', {name: 'Delete DRD', exact: true})).not.toBeInTheDocument();
	});

	it('should notify and clear only the deleted selection', async ({worker}) => {
		worker.use(
			mockDeleteResourceEndpoint({
				schema: deleteResourceRequestBodySchema.refine((body) => body?.deleteHistory === true),
				successResponse: HttpResponse.json({resourceKey: DEFINITION.decisionRequirementsKey, batchOperation: null}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const screen = await renderOperations();

		await submit(screen);

		await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeEnabled();
		expect(screen.router.state.location.search).toEqual({
			tenantId: 'tenant-a',
			businessId: 'order-1',
			evaluated: false,
			failed: false,
		});
	});

	it('should keep the newly selected version and not clear it when the previous deletion finishes', async ({
		worker,
	}) => {
		let release!: () => void;
		const released = new Promise<void>((resolve) => {
			release = resolve;
		});
		worker.use(
			http.post(endpoints.deleteResource.getUrl({resourceKey: ':resourceKey'}), async () => {
				await released;
				return HttpResponse.json({});
			}),
		);
		const screen = await renderOperationsWithVersionLink();
		await submit(screen);
		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeDisabled();

		await userEvent.click(screen.getByRole('link', {name: 'Version 2'}));
		await expect.element(screen.getByRole('button', {name: NEXT_VERSION_ACTION_NAME})).toBeEnabled();
		release();

		await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: NEXT_VERSION_ACTION_NAME})).toBeEnabled();
		expect(screen.router.state.location.search).toMatchObject({
			decisionDefinitionVersion: 2,
			tenantId: 'tenant-a',
			businessId: 'order-1',
		});
	});

	it('should clear the newly selected decision when it was deleted with the same DRD', async ({worker}) => {
		let release!: () => void;
		const released = new Promise<void>((resolve) => {
			release = resolve;
		});
		worker.use(
			mockDecisionDefinitionsByIdAndVersion([DEFINITION, SAME_DRD_DEFINITION]),
			http.post(endpoints.deleteResource.getUrl({resourceKey: ':resourceKey'}), async () => {
				await released;
				return HttpResponse.json({});
			}),
		);
		const screen = await renderOperationsWithDecisionLink();
		await submit(screen);
		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeDisabled();

		await userEvent.click(screen.getByRole('link', {name: 'My Other Decision'}));
		await expect.element(screen.getByRole('button', {name: SAME_DRD_ACTION_NAME})).toBeDisabled();
		release();

		await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: SAME_DRD_ACTION_NAME})).not.toBeInTheDocument();
		expect(screen.router.state.location.search).toEqual({
			tenantId: 'tenant-a',
			businessId: 'order-1',
			evaluated: false,
			failed: false,
		});
	});

	it.for([
		{
			name: 'a forbidden response',
			response: new HttpResponse(null, {status: 403}),
			title: "You don't have permission to perform this operation",
		},
		{
			name: 'a server error',
			response: new HttpResponse(null, {status: 500}),
			title: "Couldn't create operation",
		},
		{
			name: 'a network error',
			response: HttpResponse.error(),
			title: "Couldn't create operation",
		},
	])('should recover from $name without changing the selection', async ({response, title}, {worker}) => {
		worker.use(mockDeleteResourceEndpoint({successResponse: response}));
		const screen = await renderOperations();
		await submit(screen);

		await expect.element(screen.getByText(title, {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: ACTION_NAME})).toBeEnabled();
		expect(screen.router.state.location.href).toBe(INITIAL_ENTRY);

		worker.use(mockDeleteResourceEndpoint({successResponse: HttpResponse.json({})}));
		await submit(screen);
		await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
	});

	it('should tell the user how to get access after a forbidden response', async ({worker}) => {
		worker.use(mockDeleteResourceEndpoint({successResponse: new HttpResponse(null, {status: 403})}));
		const screen = await renderOperations();

		await submit(screen);

		await expect.element(screen.getByText('Contact the administrator if you need access.')).toBeVisible();
	});
});
