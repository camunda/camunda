/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Generates the screenshots for the "Starting processes" docs page:
 * https://docs.camunda.io/docs/next/components/tasklist/userguide/starting-processes/
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCreateProcessInstanceEndpoint,
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionEndpoint,
	mockGetProcessStartFormEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	createGetProcessDefinitionResponse,
	createProcessDefinition,
	createProcessStartFormResponse,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createProcessInstanceResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {getScreenshotPath} from '../screenshot-path';

const NOW = new Date('2024-09-05T13:32:00.000Z');

const orderProcess = createProcessDefinition({
	name: 'Order process',
	processDefinitionId: 'orderProcess',
	processDefinitionKey: '2251799813685701',
});
const newsletterProcess = createProcessDefinition({
	name: 'Subscribe to newsletter',
	processDefinitionId: 'subscribeFormProcess',
	processDefinitionKey: '2251799813685702',
	hasStartForm: true,
});

const PROCESSES = [
	createProcessDefinition({
		name: 'Business operation A',
		processDefinitionId: 'Process_0diikxu',
		processDefinitionKey: '2251799813685703',
	}),
	createProcessDefinition({
		name: 'Business operation B',
		processDefinitionId: 'Process_18z2cdf',
		processDefinitionKey: '2251799813685704',
	}),
	createProcessDefinition({
		name: 'Request annual leave',
		processDefinitionId: 'requestAnnualLeave',
		processDefinitionKey: '2251799813685705',
		hasStartForm: true,
	}),
	createProcessDefinition({
		name: 'Flight registration',
		processDefinitionId: 'flightRegistration',
		processDefinitionKey: '2251799813685706',
	}),
	orderProcess,
	createProcessDefinition({
		name: 'Car rental',
		processDefinitionId: 'registerCarForRent',
		processDefinitionKey: '2251799813685707',
		hasStartForm: true,
	}),
	createProcessDefinition({
		name: null,
		processDefinitionId: 'simpleProcess',
		processDefinitionKey: '2251799813685708',
	}),
	newsletterProcess,
	createProcessDefinition({
		name: 'Loan approval',
		processDefinitionId: 'loanApproval',
		processDefinitionKey: '2251799813685709',
	}),
];

const NEWSLETTER_FORM_SCHEMA = JSON.stringify({
	components: [
		{
			type: 'text',
			text: '# Subscribe to newsletter',
		},
		{
			key: 'name',
			label: 'Name',
			type: 'textfield',
		},
		{
			key: 'email',
			label: 'Email',
			type: 'textfield',
			validate: {required: true},
		},
	],
	type: 'default',
	id: 'subscribe-to-newsletter-form',
});

test.beforeEach(async ({network, page, tasklistProcessesPage}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({username: 'demo'})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: PROCESSES})),
		}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse()),
		}),
	);

	await page.clock.setFixedTime(NOW);
	await tasklistProcessesPage.seedHasConsentedToStartProcess();
});

test.describe('starting-processes', () => {
	test('processes page', async ({tasklistProcessesPage, page}) => {
		await tasklistProcessesPage.goto();
		await expect(tasklistProcessesPage.heading).toBeVisible();
		await expect(tasklistProcessesPage.processHeading('Order process')).toBeVisible();
		await expect(tasklistProcessesPage.processHeading('Subscribe to newsletter')).toBeVisible();
		await expect(tasklistProcessesPage.requiresFormPill(newsletterProcess.processDefinitionKey)).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'starting-processes',
				fileName: 'tasklist-processes.png',
			}),
		});
	});

	test('search processes', async ({network, tasklistProcessesPage, page}) => {
		network.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [orderProcess]})),
			}),
		);

		await tasklistProcessesPage.goto('?search=order');
		await expect(tasklistProcessesPage.searchInput).toHaveValue('order');
		await expect(tasklistProcessesPage.processHeading('Order process')).toBeVisible();
		await expect(tasklistProcessesPage.processTile(newsletterProcess.processDefinitionKey)).not.toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'starting-processes',
				fileName: 'tasklist-processes-search.png',
			}),
		});
	});

	test('start a process', async ({network, tasklistProcessesPage, page}) => {
		network.use(
			mockCreateProcessInstanceEndpoint({
				successResponse: HttpResponse.json(
					createProcessInstanceResponse({
						processDefinitionId: orderProcess.processDefinitionId,
						processDefinitionKey: orderProcess.processDefinitionKey,
						processInstanceKey: '2251799813685711',
					}),
				),
			}),
		);

		await tasklistProcessesPage.goto();
		await expect(tasklistProcessesPage.processHeading('Order process')).toBeVisible();
		await tasklistProcessesPage.processTileStartButton(orderProcess.processDefinitionKey).click();
		await expect(
			tasklistProcessesPage.header.notifications.getByNotificationTitle('Process has started'),
		).toBeVisible();
		await expect(
			tasklistProcessesPage.processTileWaitingForTasksStatus(orderProcess.processDefinitionKey),
		).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'starting-processes',
				fileName: 'tasklist-processes-start.png',
			}),
			animations: 'disabled',
		});
	});

	test('start a process with a form', async ({network, tasklistProcessesPage, page}) => {
		network.use(
			mockGetProcessDefinitionEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionResponse(newsletterProcess)),
			}),
			mockGetProcessStartFormEndpoint({
				successResponse: HttpResponse.json(createProcessStartFormResponse({schema: NEWSLETTER_FORM_SCHEMA})),
			}),
		);

		await tasklistProcessesPage.goto();
		await expect(tasklistProcessesPage.processHeading('Subscribe to newsletter')).toBeVisible();
		await tasklistProcessesPage.processTileStartButton(newsletterProcess.processDefinitionKey).click();
		await expect(tasklistProcessesPage.startProcessDialog).toBeVisible();
		await expect(tasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: 'Name'})).toBeVisible();
		await expect(tasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: /^Email/})).toBeVisible();
		await expect(tasklistProcessesPage.copyLinkButton).toBeVisible();
		await expect(tasklistProcessesPage.startProcessFormButton).toBeVisible();

		await page.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'starting-processes',
				fileName: 'tasklist-processes-start-with-form.png',
			}),
		});
	});

	test('copy link button', async ({network, tasklistProcessesPage}) => {
		network.use(
			mockGetProcessDefinitionEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionResponse(newsletterProcess)),
			}),
			mockGetProcessStartFormEndpoint({
				successResponse: HttpResponse.json(createProcessStartFormResponse({schema: NEWSLETTER_FORM_SCHEMA})),
			}),
		);

		await tasklistProcessesPage.gotoStartForm(newsletterProcess.processDefinitionKey);
		await expect(tasklistProcessesPage.startProcessDialog.getByRole('textbox', {name: /^Email/})).toBeVisible();
		await expect(tasklistProcessesPage.copyLinkButton).toBeVisible();

		await tasklistProcessesPage.copyLinkButton.screenshot({
			path: getScreenshotPath({
				baseUrl: import.meta.url,
				folder: 'starting-processes',
				fileName: 'tasklist-processes-share-button.png',
			}),
		});
	});
});
