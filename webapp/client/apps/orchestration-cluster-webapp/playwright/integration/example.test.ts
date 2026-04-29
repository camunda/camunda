/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {createEndpointMock} from '#/shared-test-modules/mock-endpoint';
import {HttpResponse} from 'msw';
import {z} from 'zod';

const mockDataEndpoint = createEndpointMock({
	endpoint: '/api/data',
	method: 'POST',
});

const requestSchema = z.object({
	key: z.string(),
});

test('should mock network requests with MSW', async ({network, page}) => {
	network.use(
		mockDataEndpoint({
			schema: requestSchema,
			failureResponse: HttpResponse.json({error: 'bad request'}, {status: 400}),
			successResponse: HttpResponse.json({data: 'value'}),
		}),
	);

	await page.goto('/');

	const response = await page.evaluate(async () => {
		const response = await fetch('/api/data', {
			method: 'POST',
			body: JSON.stringify({key: 'value'}),
		});

		return {
			ok: response.ok,
			body: await response.json(),
		};
	});

	expect(response).toEqual({
		ok: true,
		body: {data: 'value'},
	});
});
