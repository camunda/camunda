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
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);
});

test('should list the active root process instances as cases', async ({network, tasklistCasesPage, page}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({
							processInstanceKey: '2251799813685301',
							processDefinitionName: 'Home loan application',
							businessId: 'HL-28005',
						}),
						createProcessInstance({
							processInstanceKey: '2251799813685302',
							processDefinitionId: 'insurance-claim',
							processDefinitionName: null,
							businessId: 'IC-28011',
						}),
					],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await tasklistCasesPage.goto();

	await expect(tasklistCasesPage.caseLink('Home loan application, HL-28005')).toHaveAttribute(
		'href',
		'/tasklist/cases/2251799813685301',
	);
	await expect(tasklistCasesPage.caseLink('insurance-claim, IC-28011')).toHaveAttribute(
		'href',
		'/tasklist/cases/2251799813685302',
	);
	await expect(page.getByText('2 open cases')).toBeVisible();
});

test('should search the cases by business ID', async ({network, tasklistCasesPage, page}) => {
	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.noOpenCasesHeading).toBeVisible();

	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
					businessId: z.strictObject({$like: z.literal('*HL-28*')}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [createProcessInstance({processDefinitionName: 'Home loan application', businessId: 'HL-28005'})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await tasklistCasesPage.searchInput.fill('HL-28');

	await expect(tasklistCasesPage.caseLink('Home loan application, HL-28005')).toBeVisible();
	await expect(page).toHaveURL(/search=HL-28/);
});

test('should sort the cases', async ({network, tasklistCasesPage, page}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [createProcessInstance({processDefinitionName: 'Insurance claim', businessId: 'IC-28011'})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.caseLink('Insurance claim, IC-28011')).toBeVisible();

	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('businessId'), order: z.literal('ASC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [createProcessInstance({processDefinitionName: 'Home loan application', businessId: 'HL-28005'})],
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await tasklistCasesPage.columnHeaderButton('Business ID').click();

	await expect(tasklistCasesPage.caseLink('Home loan application, HL-28005')).toBeVisible();
	await expect(page).toHaveURL(/sortField=businessId/);
	await expect(page).toHaveURL(/sortOrder=asc/);
});

test('should paginate the cases', async ({network, tasklistCasesPage, page}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [createProcessInstance({processDefinitionName: 'Home loan application', businessId: 'HL-28005'})],
					page: {totalItems: 60},
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.caseLink('Home loan application, HL-28005')).toBeVisible();

	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(50), limit: z.literal(50)}),
			}),
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [createProcessInstance({processDefinitionName: 'Insurance claim', businessId: 'IC-28011'})],
					page: {totalItems: 60},
				}),
			),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);

	await tasklistCasesPage.nextPageButton.click();

	await expect(tasklistCasesPage.caseLink('Insurance claim, IC-28011')).toBeVisible();
	await expect(page).toHaveURL(/page=2/);
});

test('should display the forbidden page when case access is denied', async ({
	network,
	tasklistCasesPage,
	forbiddenPage,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: new HttpResponse(null, {status: 403}),
			failureResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
		}),
	);

	await tasklistCasesPage.goto();

	await expect(forbiddenPage.heading).toBeVisible();
});

test('should display the generic error page when cases cannot be loaded', async ({network, tasklistCasesPage}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			schema: z.strictObject({
				filter: z.strictObject({
					state: z.literal('ACTIVE'),
					parentProcessInstanceKey: z.strictObject({$exists: z.literal(false)}),
				}),
				sort: z.tuple([z.strictObject({field: z.literal('startDate'), order: z.literal('DESC')})]),
				page: z.strictObject({from: z.literal(0), limit: z.literal(50)}),
			}),
			successResponse: new HttpResponse(null, {status: 500}),
			failureResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
		}),
	);

	await tasklistCasesPage.goto();

	await expect(tasklistCasesPage.genericErrorHeading).toBeVisible();
});
