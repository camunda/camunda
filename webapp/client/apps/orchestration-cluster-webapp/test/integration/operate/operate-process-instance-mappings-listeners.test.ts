/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {z} from 'zod';
import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createListenerJob} from '#/shared-test-modules/api-mocks/jobs';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockGetProcessDefinitionXmlEndpoint, mockQueryJobsEndpoint} from '#/shared-test-modules/mock-handlers';

test('should load more listeners from the next offset when scrolling to the end of the list', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813856036'});
	const job = (index: number) =>
		createListenerJob({jobKey: `${index}`, processInstanceKey: instance.processInstanceKey});
	const jobsPage = (items: ReturnType<typeof job>[]) =>
		HttpResponse.json(
			createPaginatedResponse({
				items,
				page: {totalItems: 51, startCursor: null, endCursor: null, hasMoreTotalItems: false},
			}),
		);
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryJobsEndpoint({
			schema: z.object({page: z.object({limit: z.literal(50), from: z.literal(50)})}),
			successResponse: jobsPage([job(50)]),
			failureResponse: jobsPage(Array.from({length: 50}, (_, index) => job(index))),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/listeners');

	await expect(page.getByRole('link', {name: 'Listeners'})).toHaveAttribute('aria-current', 'page');
	await page.getByTestId('49').scrollIntoViewIfNeeded();

	await expect(page.getByTestId('50')).toBeVisible();
});

test('should keep the input mappings banner closed after reloading the page', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813856037'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(
				BPMN_XML.replace(
					'<zeebe:userTask />',
					'<zeebe:userTask /><zeebe:ioMapping><zeebe:input source="=orderId" target="localOrderId" /></zeebe:ioMapping>',
				),
			),
		}),
	);
	const banner = page.getByText('Input mappings are defined while modelling the diagram.');

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/input-mappings?elementId=task-1');

	await expect(page.getByRole('link', {name: 'Input Mappings'})).toHaveAttribute('aria-current', 'page');
	await expect(page.getByText('localOrderId')).toBeVisible();
	await expect(banner).toBeVisible();

	await page.getByRole('button', {name: 'Close'}).click();
	await expect(banner).toBeHidden();
	await page.reload();

	await expect(page.getByText('localOrderId')).toBeVisible();
	await expect(banner).toBeHidden();
});
