/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';

function createProcessDefinitionsRequestSchema(filter: Record<string, z.ZodType> = {}) {
	return z.strictObject({
		filter: z.strictObject({isLatestVersion: z.literal(true), ...filter}),
		page: z.strictObject({limit: z.literal(12)}),
	});
}

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema(),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);
});

test('should render the Tasklist processes page with navigation', async ({shadcnTasklistProcessesPage}) => {
	await shadcnTasklistProcessesPage.goto();

	await expect(shadcnTasklistProcessesPage.heading).toBeVisible();
	await expect(shadcnTasklistProcessesPage.description).toBeVisible();
});

test('should display available processes with their names, IDs, form requirements, start buttons, and more-results control', async ({
	network,
	shadcnTasklistProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema(),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({
							name: 'Invoice review',
							processDefinitionId: 'invoice-review',
							processDefinitionKey: '1',
							hasStartForm: true,
						}),
						createProcessDefinition({
							name: null,
							processDefinitionId: 'order-approval',
							processDefinitionKey: '2',
						}),
					],
					page: {endCursor: 'next-page', hasMoreTotalItems: true},
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.goto();

	await expect(shadcnTasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
	await expect(page.getByText('invoice-review', {exact: true})).toBeVisible();
	await expect(shadcnTasklistProcessesPage.processHeading('order-approval')).toBeVisible();
	// exact: true — Radix Select renders a visually-hidden native <select> mirroring
	// its items for autofill; its "Requires form input to start" <option> would
	// otherwise substring-match this query too.
	await expect(page.getByText('Requires form input', {exact: true})).toBeVisible();
	await expect(shadcnTasklistProcessesPage.startProcessButtons).toHaveCount(2);
	for (const button of await shadcnTasklistProcessesPage.startProcessButtons.all()) {
		await expect(button).toBeEnabled();
	}
	await expect(shadcnTasklistProcessesPage.loadMoreButton).toBeVisible();
});

test('should display the appropriate empty state for unfiltered and filtered process lists', async ({
	network,
	shadcnTasklistProcessesPage,
}) => {
	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({
				processDefinitionId: z.strictObject({$like: z.literal('*missing*')}),
			}),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);
	await shadcnTasklistProcessesPage.searchInput.fill('missing');
	await expect(shadcnTasklistProcessesPage.noMatchingProcessesHeading).toBeVisible();
});

test('should search and filter processes while preserving the selected filters', async ({
	network,
	shadcnTasklistProcessesPage,
	page,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(true)}),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.goto('?hasStartForm=yes');
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({
				hasStartForm: z.literal(true),
				processDefinitionId: z.strictObject({$like: z.literal('*invoice*')}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Invoice review', processDefinitionKey: '1'})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.searchInput.fill('invoice');

	await expect(shadcnTasklistProcessesPage.processHeading('Invoice review')).toBeVisible();
	await expect(page).toHaveURL(/hasStartForm=yes/);
	await expect(page).toHaveURL(/search=invoice/);

	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(true)}),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.searchInput.fill('');
	await expect(page).not.toHaveURL(/search=/);
	await expect(shadcnTasklistProcessesPage.unpublishedProcessesHeading).toBeVisible();

	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({hasStartForm: z.literal(false)}),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Order approval', processDefinitionKey: '2'})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.processFilter.click();
	await page.getByRole('option', {name: 'Does not require form input to start'}).click();

	await expect(shadcnTasklistProcessesPage.processHeading('Order approval')).toBeVisible();
	await expect(page).toHaveURL(/hasStartForm=no/);
});

test('should use the default tenant and update the process list when another tenant is selected', async ({
	network,
	shadcnTasklistProcessesPage,
	page,
}) => {
	const tenants = [
		{tenantId: '<default>', name: 'Default', description: null},
		{tenantId: 'tenant-a', name: 'Tenant A', description: null},
	];
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({tenants}))}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['tasklist']},
					deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0},
				}),
			),
		}),
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('<default>')}),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Default tenant process', processDefinitionKey: '1'})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.processHeading('Default tenant process')).toBeVisible();

	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: createProcessDefinitionsRequestSchema({tenantId: z.literal('tenant-a')}),
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Tenant A process', processDefinitionKey: '2'})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await shadcnTasklistProcessesPage.tenantFilter.click();
	await page.getByRole('option', {name: 'Tenant A - tenant-a'}).click();

	await expect(shadcnTasklistProcessesPage.processHeading('Tenant A process')).toBeVisible();
	await expect(page).toHaveURL(/tenantId=tenant-a/);
});
