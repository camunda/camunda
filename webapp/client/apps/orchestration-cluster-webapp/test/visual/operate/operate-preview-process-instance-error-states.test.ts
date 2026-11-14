/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {mockGetProcessInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';

test.beforeEach(({network}) => {
	network.use(...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})));
});

test('should match the process instance forbidden page snapshot', async ({network, page}) => {
	network.use(
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
		}),
	);

	await page.goto(`/operate-preview/processes/${PROCESS_INSTANCE_ID}`);

	await expect(page.getByRole('heading', {name: 'You do not have permission to view this information'})).toBeVisible();
	await expect(page.getByRole('main')).toHaveScreenshot();
});

test('should match the process instance generic error page snapshot', async ({network, page}) => {
	network.use(mockGetProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await page.goto(`/operate-preview/processes/${PROCESS_INSTANCE_ID}`);

	await expect(page.getByRole('button', {name: 'Try again'})).toBeVisible();
	await expect(page.getByRole('main')).toHaveScreenshot();
});
