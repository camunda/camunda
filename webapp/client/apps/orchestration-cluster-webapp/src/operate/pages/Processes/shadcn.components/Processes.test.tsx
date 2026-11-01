/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense} from 'react';
import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {useSearch} from '@tanstack/react-router';
import {queryProcessInstancesRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createGetProcessDefinitionStatisticsResponse,
	createProcessDefinitionStatistic,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {setVariableConditions} from '../VariablesFilter/variableFilterStore';
import {processesSearchSchema} from '../processesFilter';
import {Processes} from './Processes';

const PATH = '/operate-preview/processes';

const Harness = () => {
	const search = processesSearchSchema.parse(useSearch({strict: false}));
	return (
		<div style={{height: '100vh'}}>
			<Suspense fallback={null}>
				<Processes search={search} />
			</Suspense>
		</div>
	);
};

const BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="d" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="order" isExecutable="true">
    <bpmn:startEvent id="start" name="Start" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="order">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="152" y="82" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

const ORDER_V2 = createProcessDefinition({processDefinitionId: 'order', name: 'Order', version: 2});

describe('<Processes /> (design system)', () => {
	it.beforeEach(({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [ORDER_V2]})),
			}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: []})),
			}),
			mockQueryBatchOperationItemsEndpoint({
				successResponse: HttpResponse.json({items: [], page: {totalItems: 0}}),
			}),
		);
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the sr-only processes title, the diagram panel, and the instances table', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await expect.element(screen.getByRole('heading', {name: 'Processes'})).toBeInTheDocument();
		await expect.element(screen.getByRole('region', {name: 'Diagram Panel'})).toBeVisible();
		await expect.element(screen.getByText('There is no Process selected')).toBeVisible();
		await expect.element(screen.getByRole('region', {name: 'Process Instances Panel'})).toBeVisible();
	});

	it('should write the selected process to the URL', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByLabelText('Name'));
		await userEvent.click(screen.getByRole('option', {name: 'Order'}));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({process: 'order', version: 2});
	});

	it('should show only the Running states as checked by default', async () => {
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?active=true&incidents=true&suspended=true`,
		});

		await expect.element(screen.getByRole('checkbox', {name: 'Running Instances'})).toBeChecked();
		await expect.element(screen.getByRole('checkbox', {name: 'Finished Instances'})).not.toBeChecked();
	});

	it('should check both finished states when Finished Instances is checked', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByRole('checkbox', {name: 'Finished Instances'}));

		await expect.poll(() => screen.router.state.location.search).toMatchObject({completed: true, canceled: true});
	});

	it('should add an optional filter from the menu and serialize it to the URL', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Error Message'}));
		await userEvent.fill(screen.getByLabelText('Error Message'), 'timeout');

		await expect.poll(() => screen.router.state.location.search).toMatchObject({errorMessage: 'timeout'});
	});

	it('should reset all filters', async () => {
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?process=order&version=1&completed=true`,
		});

		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({active: true, suspended: true, incidents: true});
	});

	it('should show the selected process name when the same process exists in two tenants', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {...createSystemConfiguration().deployment, isMultiTenancyEnabled: true},
				}),
			),
		);
		worker.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: 'tenant-a', name: 'Tenant A', description: null},
							{tenantId: 'tenant-b', name: 'Tenant B', description: null},
						],
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({processDefinitionId: 'order', name: 'Order', version: 2, tenantId: 'tenant-a'}),
							createProcessDefinition({processDefinitionId: 'order', name: 'Order', version: 2, tenantId: 'tenant-b'}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?tenantId=all&process=order&version=2`,
		});

		await expect.element(screen.getByLabelText('Name')).toHaveTextContent('Order');
	});

	it('should keep showing an element filter that is absent from the loaded diagram', async () => {
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?process=order&elementId=removedTask`,
		});

		await expect.element(screen.getByLabelText('Element')).toHaveTextContent('removedTask');
	});

	it('should select and clear a diagram element in the URL, filter instances by it and restore it through history', async ({
		worker,
	}) => {
		const requestBodies: unknown[] = [];
		worker.use(
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createGetProcessDefinitionStatisticsResponse([
						createProcessDefinitionStatistic({elementId: 'start', active: 1}),
					]),
				),
			}),
			mockQueryProcessInstancesEndpoint({
				schema: queryProcessInstancesRequestBodySchema.refine((body) => {
					requestBodies.push(body);
					return true;
				}),
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: []})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const screen = await renderWithRouter(Harness, {
			path: PATH,
			initialEntry: `${PATH}?process=order&version=2&active=true`,
		});
		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		const getNode = () => document.querySelector<SVGElement>('[data-element-id="start"]')!;

		await userEvent.click(getNode());

		await expect.poll(() => screen.router.state.location.search).toMatchObject({elementId: 'start'});
		await expect
			.poll(() => requestBodies.some((body) => JSON.stringify(body).includes('"elementId":{"$eq":"start"}')))
			.toBe(true);

		await userEvent.click(getNode());

		await expect.poll(() => screen.router.state.location.search).not.toHaveProperty('elementId');

		screen.router.history.back();

		await expect.poll(() => screen.router.state.location.search).toMatchObject({elementId: 'start'});
		await expect.element(screen.getByLabelText('Element')).toHaveTextContent('Start');
	});

	it('should move focus to the date range group when it is added from the menu', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Start Date Range'}));

		await expect.element(screen.getByRole('group', {name: 'Start Date Range'})).toHaveFocus();
	});

	it('should keep an unrelated invalid draft when an active optional filter is removed', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: `${PATH}?errorMessage=timeout`});

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process Instance Key(s)'}));
		await userEvent.fill(screen.getByLabelText('Process Instance Key(s)'), 'abc');
		await userEvent.click(screen.getByRole('button', {name: 'Remove Error Message Filter'}));

		await expect.poll(() => screen.router.state.location.search).not.toHaveProperty('errorMessage');
		await expect.element(screen.getByLabelText('Process Instance Key(s)')).toHaveValue('abc');
	});

	it('should discard an invalid draft when the filters are reset', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: `${PATH}?errorMessage=timeout`});

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Process Instance Key(s)'}));
		await userEvent.fill(screen.getByLabelText('Process Instance Key(s)'), 'abc');
		await userEvent.click(screen.getByRole('button', {name: 'Remove Error Message Filter'}));
		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		await expect.element(screen.getByLabelText('Process Instance Key(s)')).not.toBeInTheDocument();
	});

	it('should move focus to the variables group when it is added from the menu', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Variables'}));

		await expect.element(screen.getByRole('group', {name: 'Variables'})).toHaveFocus();
	});

	it('should store an inline equals variable condition', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByRole('button', {name: 'More filters'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Variables'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'status');
		await userEvent.fill(screen.getByRole('textbox', {name: 'Value'}), '"open"');

		await expect.poll(() => sessionStorage.getItem('operate.variableFilter.conditions')).toContain('status');
	});

	it('should clear the variable conditions when filters are reset', async () => {
		setVariableConditions([{name: 'status', operator: 'equals', value: '"open"'}]);
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).not.toBeInTheDocument();
	});
});
