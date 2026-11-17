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
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockQueryProcessInstanceIncidentsEndpoint} from '#/shared-test-modules/mock-handlers';

test('should show the process instance incidents and load the next page when scrolling to the end', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813856035', hasIncident: true});
	const incident = (index: number) =>
		createIncident({
			incidentKey: `${index}`,
			processInstanceKey: instance.processInstanceKey,
			errorMessage: `Failure ${String(index).padStart(2, '0')}`,
		});
	const incidentsPage = (items: ReturnType<typeof incident>[]) =>
		HttpResponse.json(
			createPaginatedResponse({
				items,
				page: {totalItems: 51, startCursor: null, endCursor: null, hasMoreTotalItems: false},
			}),
		);
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryProcessInstanceIncidentsEndpoint({
			schema: z.object({page: z.object({from: z.literal(50)})}),
			successResponse: incidentsPage([incident(50)]),
			failureResponse: incidentsPage(Array.from({length: 50}, (_, index) => incident(index))),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/incidents');

	await expect(page.getByRole('link', {name: /Incidents/})).toHaveAttribute('aria-current', 'page');
	await expect(page.getByText('51 results')).toBeVisible();
	await page.getByRole('row').filter({hasText: 'Failure 49'}).scrollIntoViewIfNeeded();

	await expect(page.getByRole('row').filter({hasText: 'Failure 50'})).toBeVisible();
});
