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
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
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
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="d" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="order" isExecutable="true">
    <bpmn:startEvent id="start" name="Start" />
  </bpmn:process>
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
		);
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the sr-only processes title and the remaining placeholders', async () => {
		const screen = await renderWithRouter(Harness, {path: PATH, initialEntry: PATH});

		await expect.element(screen.getByRole('heading', {name: 'Processes'})).toBeInTheDocument();
		await expect.element(screen.getByText('Diagram panel placeholder')).toBeVisible();
		await expect.element(screen.getByText('Instances table placeholder')).toBeVisible();
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
});
