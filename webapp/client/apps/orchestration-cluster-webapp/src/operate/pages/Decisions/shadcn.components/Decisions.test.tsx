/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {useSearch} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {HttpResponse} from 'msw';
import {Toaster, toast} from '@camunda/design-system';
import {
	mockDeleteResourceEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {DMN_XML} from '#/shared-test-modules/api-mocks/decision-definition-xmls';
import {createQueryDecisionInstancesResponse} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {Decisions} from './Decisions';

const DecisionsWithSearch = () => {
	const search = useSearch({strict: false});
	return (
		<>
			<Decisions search={{evaluated: true, failed: true, ...search}} />
			<Toaster />
			<Notifications />
		</>
	);
};

const DecisionsInFullHeightShell = () => (
	<div style={{height: '100vh'}}>
		<DecisionsWithSearch />
	</div>
);

function mockInstances() {
	return mockQueryDecisionInstancesEndpoint({
		successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
	});
}

function mockTwoInvoiceVersions() {
	return mockQueryDecisionDefinitionsEndpoint({
		successResponse: HttpResponse.json(
			createQueryDecisionDefinitionsResponse({
				items: [
					createDecisionDefinition({
						name: 'Invoice Classification',
						decisionDefinitionId: 'invoice',
						decisionDefinitionKey: 'key-1',
						version: 1,
					}),
					createDecisionDefinition({
						name: 'Invoice Classification',
						decisionDefinitionId: 'invoice',
						decisionDefinitionKey: 'key-2',
						version: 2,
					}),
				],
			}),
		),
	});
}

describe('<Decisions />', () => {
	it.beforeEach(({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: []})),
			}),
		);
	});

	afterEach(() => {
		vi.useRealTimers();
		sessionStorage.clear();
		notificationsStore.reset();
		toast.dismiss();
	});

	it('should render the sr-only decisions title', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByRole('heading', {name: 'Decisions'})).toBeInTheDocument();
	});

	it('should render the filters panel with the optional filters menu, the decision panel and the instances table', async ({
		worker,
	}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse()),
			}),
		);
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByRole('button', {name: 'More filters'})).toBeVisible();
		await expect.element(screen.getByText('Select a decision')).toBeVisible();
		await expect.element(screen.getByText('Decision instances')).toBeVisible();
	});

	it('should show optional filters that are active in the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
	});

	it('should enable reset when optional filters are active in the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should enable reset when a decision definition is selected in the URL', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [createDecisionDefinition({decisionDefinitionId: 'invoice', version: 1})],
					}),
				),
			}),
		);
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should write edited optional filters to the URL and keep unrelated search state', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&tenantId=tenant-a&failed=false',
		});

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
		await userEvent.fill(screen.getByLabelText('Business ID', {exact: true}), 'order-2');
		await vi.advanceTimersByTimeAsync(750);

		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({
				businessId: 'eq_order-2',
				tenantId: 'tenant-a',
				failed: false,
			});

		await userEvent.clear(screen.getByLabelText('Business ID', {exact: true}));
		await vi.advanceTimersByTimeAsync(750);

		await expect.poll(() => screen.router.state.location.search).toEqual({tenantId: 'tenant-a', failed: false});
	});

	it('should clear a removed filter from the URL even when another filter field is invalid', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&processInstanceKey=%22123%22',
		});

		await userEvent.fill(screen.getByLabelText('Process instance key', {exact: true}), 'abc');
		await expect
			.element(screen.getByLabelText('Process instance key', {exact: true}))
			.toHaveAttribute('aria-invalid', 'true');
		await userEvent.click(screen.getByRole('button', {name: 'Remove Business ID filter'}));

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		await expect.poll(() => screen.router.state.location.search).not.toHaveProperty('businessId');
	});

	it('should keep unrelated edits when another filter is removed', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&processInstanceKey=%22123%22',
		});

		await userEvent.fill(screen.getByLabelText('Process instance key', {exact: true}), 'abc');
		await userEvent.click(screen.getByRole('button', {name: 'Remove Business ID filter'}));

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Process instance key', {exact: true})).toHaveValue('abc');
	});
	it('should restore URL values when navigating back with a pending draft', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});
		await userEvent.fill(screen.getByLabelText('Business ID', {exact: true}), 'order-2');
		await vi.advanceTimersByTimeAsync(750);
		vi.useRealTimers();
		await expect.poll(() => screen.router.state.location.search).toHaveProperty('businessId', 'eq_order-2');

		await userEvent.fill(screen.getByLabelText('Business ID', {exact: true}), 'order-3');
		screen.router.history.back();

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
		await expect.poll(() => screen.router.state.location.search).toHaveProperty('businessId', 'eq_order-1');
	});

	it('should discard an invalid draft when resetting after removing an empty filter', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {path: '/operate-preview/decisions'});
		const moreFilters = screen.getByRole('button', {name: 'More filters'});

		await userEvent.click(moreFilters);
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process instance key'}));
		await userEvent.fill(screen.getByLabelText('Process instance key', {exact: true}), 'abc');
		await userEvent.click(moreFilters);
		await userEvent.click(screen.getByRole('menuitem', {name: 'Business ID'}));
		await userEvent.click(screen.getByRole('button', {name: 'Remove Business ID filter'}));
		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));
		await userEvent.click(moreFilters);
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process instance key'}));

		await expect.element(screen.getByLabelText('Process instance key', {exact: true})).toHaveValue('');
	});

	it('should discard an invalid draft when resetting', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {path: '/operate-preview/decisions'});

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process instance key'}));
		await userEvent.fill(screen.getByLabelText('Process instance key', {exact: true}), 'abc');
		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));
		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process instance key'}));

		await expect.element(screen.getByLabelText('Process instance key', {exact: true})).toHaveValue('');
	});

	it('should enable reset when a sort is applied in the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?sort=evaluationDate%2Basc',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should render the decision filters with the version disabled until a decision is selected', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByText('Instances states')).toBeVisible();
		await expect.element(screen.getByLabelText('Name')).toBeVisible();
		await expect.element(screen.getByLabelText('Version')).toBeDisabled();
	});

	it('should write the instance state checkboxes to the URL', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByLabelText('Failed')).toBeVisible();
		await userEvent.click(screen.getByLabelText('Failed'));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({failed: false});
	});

	it('should list the versions of the selected decision and enable the version select', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice', version: 1}),
							createDecisionDefinition({decisionDefinitionId: 'invoice', name: 'Invoice', version: 2}),
						],
					}),
				),
			}),
		);
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice',
		});

		await expect.element(screen.getByLabelText('Version')).toBeEnabled();
		await userEvent.click(screen.getByLabelText('Version'));
		await expect.element(screen.getByRole('option', {name: '2'})).toBeVisible();
		await userEvent.click(screen.getByRole('option', {name: '2'}));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({decisionDefinitionVersion: 2});
	});

	it('should remove a single optional filter from the URL when its remove button is clicked', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&failed=false',
		});

		await expect.element(screen.getByRole('button', {name: 'Remove Business ID filter'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Remove Business ID filter'}));

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		expect(screen.router.state.location.search).not.toHaveProperty('businessId');
		expect(screen.router.state.location.search).toMatchObject({failed: false});
	});

	it('should remove active optional filters when resetting', async ({worker}) => {
		worker.use(mockInstances());
		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
	});

	it('should show the selected decision in the decision panel', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({name: 'Invoice Classification', decisionDefinitionId: 'invoice', version: 1}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice&decisionDefinitionVersion=1',
		});

		await expect.element(screen.getByRole('heading', {name: 'Invoice Classification'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /Decision ID/})).toBeVisible();
	});

	it('should ask for a single version when all versions of a decision are selected', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [createDecisionDefinition({name: 'Invoice Classification', decisionDefinitionId: 'invoice'})],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice',
		});

		await expect
			.element(screen.getByText('There is more than one version selected for decision "Invoice Classification"'))
			.toBeVisible();
	});

	it('should explain that the selected version exists in multiple tenants', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({
								name: 'Invoice Classification',
								decisionDefinitionId: 'invoice',
								version: 1,
								tenantId: 'tenant-a',
							}),
							createDecisionDefinition({
								name: 'Invoice Classification',
								decisionDefinitionId: 'invoice',
								version: 1,
								tenantId: 'tenant-b',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(DecisionsWithSearch, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice&decisionDefinitionVersion=1',
		});

		await expect
			.element(screen.getByText('Decision "Invoice Classification" exists in more than one tenant'))
			.toBeVisible();
	});

	it('should show the delete action only when a single decision version is selected', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [
							createDecisionDefinition({name: 'Invoice Classification', decisionDefinitionId: 'invoice', version: 1}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(DecisionsInFullHeightShell, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice&decisionDefinitionVersion=1',
		});

		await expect.element(screen.getByRole('button', {name: /Delete decision definition/})).toBeVisible();

		await userEvent.click(screen.getByLabelText('Version'));
		await userEvent.click(screen.getByRole('option', {name: 'All versions'}));

		await expect.element(screen.getByRole('button', {name: /Delete decision definition/})).not.toBeInTheDocument();
	});

	it('should keep a newly selected version blocked while the previous deletion is pending', async ({worker}) => {
		worker.use(
			mockInstances(),
			mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(DMN_XML)}),
			mockDeleteResourceEndpoint({successResponse: HttpResponse.json({}), delay: 'infinite'}),
			mockTwoInvoiceVersions(),
		);
		const screen = await renderWithRouter(DecisionsInFullHeightShell, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice&decisionDefinitionVersion=1',
		});

		await userEvent.click(screen.getByRole('button', {name: /Delete decision definition/}));
		await userEvent.click(screen.getByText(/Yes, I confirm I want to delete this DRD/));
		await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await userEvent.click(screen.getByLabelText('Version'));
		await userEvent.click(screen.getByRole('option', {name: '2'}));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({decisionDefinitionVersion: 2});
		await expect.element(screen.getByRole('button', {name: /Delete decision definition/})).toBeDisabled();
	});
});
