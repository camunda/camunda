/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {queryProcessDefinitionsRequestBodySchema, type ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {
	mockDeleteResourceEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createGetProcessDefinitionStatisticsResponse} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Processes} from './Processes';
import {registerElementFilterTests} from '../../../../test/fixtures/operate/elementFilterTestCases';

const PROCESS_DEFINITIONS = HttpResponse.json(
	createQueryProcessDefinitionsResponse({
		items: [
			createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 2}),
			createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 1}),
			createProcessDefinition({name: 'Payment Process', processDefinitionId: 'payment-process', version: 1}),
		],
	}),
);

type RenderProps = {
	process?: string;
	version?: number;
	elementId?: string;
	active?: boolean;
	incidents?: boolean;
	completed?: boolean;
	canceled?: boolean;
	suspended?: boolean;
};

function renderPage(props?: RenderProps) {
	return renderWithRouter(
		() => (
			<Processes
				process={props?.process}
				version={props?.version}
				elementId={props?.elementId}
				active={props?.active ?? true}
				incidents={props?.incidents ?? true}
				suspended={props?.suspended ?? true}
				completed={props?.completed ?? false}
				canceled={props?.canceled ?? false}
			/>
		),
		{path: '/operate/processes'},
	);
}

const EMPTY_PROCESS_INSTANCES = HttpResponse.json(createQueryProcessInstancesResponse());

registerElementFilterTests();

describe('<Processes />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the filter sections', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('Instances States')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Name'})).toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Running Instances'})).toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Finished Instances'})).toBeVisible();
	});

	it('should disable the version dropdown until a process is selected', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('combobox', {name: 'Version'})).toBeDisabled();
		await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
	});

	it('should enable the version dropdown once a process is selected', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
		);

		const screen = await renderPage({process: 'order-process'});

		await expect.element(screen.getByRole('combobox', {name: 'Version'})).not.toBeDisabled();
	});

	it('should enable the element combobox after the selected definition XML loads', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML), delay: 1500}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);

		const screen = await renderPage({process: 'order-process', version: 1});

		await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeDisabled();
		await expect.element(screen.getByRole('combobox', {name: 'Element'})).toBeEnabled();
	});

	it('should navigate resetting version and elementId when a process is selected', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
			}),
		);

		const screen = await renderPage({process: 'payment-process', version: 1, elementId: 'some-element'});

		// force: the FiltersPanel Container is absolutely positioned (verbatim legacy port) and
		// overlaps the header/footer outside the full app shell's height constraints in this isolated render.
		// Type + Enter instead of clicking the popover option, which sits behind the same overlap.
		const nameCombobox = screen.getByRole('combobox', {name: 'Name'});
		await nameCombobox.click({force: true});
		await nameCombobox.fill('Order Process');
		await userEvent.keyboard('{Enter}');

		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;
		await expect.poll(getSearch).toMatchObject({process: 'order-process'});
		expect(getSearch().version).toBeUndefined();
		expect(getSearch().elementId).toBeUndefined();
	});

	it('should navigate resetting elementId when a version is selected', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
		);

		const screen = await renderPage({process: 'order-process', elementId: 'some-element'});

		// The FiltersPanel Container is absolutely positioned (verbatim legacy port) and overlaps
		// the header/footer outside the full app shell's height constraints in this isolated render,
		// so a real click on the Version toggle hits the overlapping footer instead. Reach it via
		// keyboard focus from the Name field instead, which `fill` focuses directly regardless of the overlap.
		await screen.getByRole('combobox', {name: 'Name'}).fill('Order Process');
		await userEvent.keyboard('{Tab}{ArrowDown}{ArrowDown}{Enter}');

		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;
		await expect.poll(getSearch).toMatchObject({version: 2});
		expect(getSearch().elementId).toBeUndefined();
	});

	describe('parent checkbox derivation', () => {
		it('shows the running checkbox checked when both active and incidents are true', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage({active: true, incidents: true});

			await expect.element(screen.getByRole('checkbox', {name: 'Running Instances'})).toBeChecked();
		});

		it('shows the running checkbox indeterminate when only one of active/incidents is true', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage({active: true, incidents: false});

			await expect.element(screen.getByRole('checkbox', {name: 'Running Instances'})).not.toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Running Instances'})).toBePartiallyChecked();
		});

		it('shows the finished checkbox checked when both completed and canceled are true', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage({completed: true, canceled: true});

			await expect.element(screen.getByRole('checkbox', {name: 'Finished Instances'})).toBeChecked();
		});

		it('shows the finished checkbox indeterminate when only one of completed/canceled is true', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage({completed: true, canceled: false});

			await expect.element(screen.getByRole('checkbox', {name: 'Finished Instances'})).not.toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Finished Instances'})).toBePartiallyChecked();
		});

		it('shows the running checkbox indeterminate when suspended alone differs from active/incidents', async ({
			worker,
		}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage({active: true, incidents: true, suspended: false});

			await expect.element(screen.getByRole('checkbox', {name: 'Running Instances'})).not.toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Running Instances'})).toBePartiallyChecked();
		});
	});

	describe('suspended filter', () => {
		it('defaults to visible and checked', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('checkbox', {name: 'Suspended'})).toBeChecked();
			await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
		});

		it('updates the URL search state when toggled', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage();
			const suspendedCheckbox = screen.getByRole('checkbox', {name: 'Suspended'});
			await expect.element(suspendedCheckbox).toBeChecked();
			await userEvent.click(screen.getByTestId('expanded-panel').getByText('Suspended'));

			const getSearch = () => screen.router.state.location.search as Record<string, unknown>;
			await expect.poll(getSearch).toMatchObject({suspended: false});
		});
	});

	describe('reset button', () => {
		it('is disabled at the default filter state', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
		});

		it('is enabled once a process is selected', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage({process: 'order-process'});

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).not.toBeDisabled();
		});

		it('is enabled once a non-default instance state checkbox is set', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			);

			const screen = await renderPage({completed: true});

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).not.toBeDisabled();
		});
	});

	describe('definition deletion', () => {
		const definition = createProcessDefinition({
			name: 'Order Process',
			processDefinitionId: 'order-process',
			version: 2,
		});

		function mockSelectedDefinition(selected: ProcessDefinition) {
			return mockQueryProcessDefinitionsEndpoint({
				schema: queryProcessDefinitionsRequestBodySchema.refine((body) => body.filter?.state !== 'DRAINING'),
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [selected]})),
				failureResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			});
		}

		afterEach(() => notificationsStore.reset());

		it('should show the deleted definition once its deletion is created', async ({worker}) => {
			worker.use(
				mockSelectedDefinition(definition),
				mockQueryProcessInstancesEndpoint({successResponse: EMPTY_PROCESS_INSTANCES}),
				mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
				mockGetProcessDefinitionStatisticsEndpoint({
					successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
				}),
				mockDeleteResourceEndpoint({
					successResponse: HttpResponse.json({resourceKey: definition.processDefinitionKey, batchOperation: null}),
				}),
			);
			const screen = await renderWithRouter(
				() => (
					<>
						<Processes
							process="order-process"
							version={2}
							active
							incidents
							suspended
							completed={false}
							canceled={false}
						/>
						<Notifications />
					</>
				),
				{path: '/operate/processes'},
			);

			await userEvent.click(
				screen.getByRole('button', {name: 'Delete Process Definition "Order Process - Version 2"'}),
			);
			await userEvent.click(screen.getByText('Yes, I confirm I want to delete this process definition.'));
			worker.use(mockSelectedDefinition({...definition, state: 'DELETED'}));
			await userEvent.click(screen.getByRole('button', {name: 'Delete', exact: true}));

			await expect.element(screen.getByText('Operation created', {exact: true})).toBeVisible();
			await expect.element(screen.getByText('Deleted', {exact: true})).toBeVisible();
			await expect
				.element(screen.getByRole('button', {name: 'Delete Process Definition History "Order Process - Version 2"'}))
				.toBeEnabled();
		});
	});
});
