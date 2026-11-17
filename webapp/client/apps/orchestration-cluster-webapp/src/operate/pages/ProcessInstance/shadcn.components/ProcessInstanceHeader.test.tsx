/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {ProcessInstanceContext} from '../useProcessInstancePage';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';

const PROCESS_INSTANCE_ID = '2251799813685249';
const PROCESS_XML_WITH_CALL_ACTIVITY =
	'<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="my-process"><callActivity id="call-activity" /></process></definitions>';

function renderHeader(instance: ReturnType<typeof createProcessInstance>) {
	return renderWithRouter(
		() => (
			<ProcessInstanceContext
				value={{processInstanceId: instance.processInstanceKey, processInstance: instance, search: {}, selection: {}}}
			>
				<ProcessInstanceHeader operations={<button type="button">Operation slot</button>} />
			</ProcessInstanceContext>
		),
		{
			path: '/operate-preview/processes/$processInstanceId',
			initialEntry: `/operate-preview/processes/${instance.processInstanceKey}`,
		},
	);
}

describe('<ProcessInstanceHeader /> (DS)', () => {
	beforeEach(() => {
		vi.spyOn(window, 'matchMedia').mockImplementation((query) => ({
			matches: false,
			media: query,
			onchange: null,
			addEventListener: vi.fn(),
			removeEventListener: vi.fn(),
			addListener: vi.fn(),
			removeListener: vi.fn(),
			dispatchEvent: vi.fn(),
		}));
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: false, maxRequestSize: 0},
				}),
			),
		);
	});

	afterEach(async () => {
		await cleanup();
		vi.restoreAllMocks();
		sessionStorage.clear();
		localStorage.clear();
	});

	it('should render the instance details, tenant, waiting status, and operations slot', async ({worker}) => {
		const instance = createProcessInstance({
			processInstanceKey: PROCESS_INSTANCE_ID,
			processDefinitionName: 'Order',
			processDefinitionVersionTag: 'v1-tag',
			businessId: 'order-4711',
			tenantId: 'tenant-a',
		});
		worker.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({items: [{elementId: instance.processDefinitionId, waitingCount: 2}]}),
				),
			}),
			...processInstanceHeaderHandlers(instance),
		);

		const screen = await renderHeader(instance);

		await expect.element(screen.getByRole('table')).toBeVisible();
		await expect.element(screen.getByText('Order', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('2 waiting')).toBeVisible();
		await expect.element(screen.getByText('Tenant A', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('v1-tag')).toBeVisible();
		await expect.element(screen.getByText('order-4711')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Operation slot'})).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: /View process .*Tenant A/}))
			.toHaveAttribute('href', expect.stringContaining('tenantId=tenant-a'));
	});

	it('should link to called instances and expand the process filters panel', async ({worker}) => {
		storeStateLocally('operate.panelStates', {
			isProcessesFiltersCollapsed: true,
			isProcessInstanceOperationsCollapsed: true,
		});
		const instance = createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID});
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_XML_WITH_CALL_ACTIVITY)}),
			...processInstanceHeaderHandlers(instance),
		);

		const screen = await renderHeader(instance);
		const calledInstancesLink = screen.getByRole('link', {name: 'View all called instances'});

		await expect
			.element(calledInstancesLink)
			.toHaveAttribute('href', expect.stringContaining(`parentProcessInstanceKey=%22${PROCESS_INSTANCE_ID}%22`));

		await userEvent.click(calledInstancesLink);

		expect(getStateLocally('operate.panelStates')).toEqual({
			isProcessesFiltersCollapsed: false,
			isProcessInstanceOperationsCollapsed: true,
		});
	});
});
