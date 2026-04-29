/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {z} from 'zod';
import {createEndpointMock} from '#/shared-test-modules/mock-endpoint';
import {it} from './test-extend';

const mockDataEndpoint = createEndpointMock({
	endpoint: '/api/data',
	method: 'POST',
});

async function postData(payload: unknown) {
	return fetch('/api/data', {
		method: 'POST',
		body: JSON.stringify(payload),
	});
}

describe('createEndpointMock', () => {
	it('should return success response without validating the request payload', async ({worker}) => {
		worker.use(
			mockDataEndpoint({
				successResponse: HttpResponse.json({data: 'value'}),
			}),
		);

		const response = await postData({invalid: true});

		expect(response.ok).toBe(true);
		await expect(response.json()).resolves.toEqual({data: 'value'});
	});

	it('should return success response when the request payload matches the schema', async ({worker}) => {
		worker.use(
			mockDataEndpoint({
				schema: z.object({
					key: z.string(),
				}),
				failureResponse: HttpResponse.json({error: 'bad request'}, {status: 400}),
				successResponse: HttpResponse.json({data: 'value'}),
			}),
		);

		const response = await postData({key: 'value'});

		expect(response.ok).toBe(true);
		await expect(response.json()).resolves.toEqual({data: 'value'});
	});

	it('should return failure response when the request payload does not match the schema', async ({worker}) => {
		worker.use(
			mockDataEndpoint({
				schema: z.object({
					key: z.string(),
				}),
				failureResponse: HttpResponse.json({error: 'bad request'}, {status: 400}),
				successResponse: HttpResponse.json({data: 'value'}),
			}),
		);

		const response = await postData({key: 123});

		expect(response.status).toBe(400);
		await expect(response.json()).resolves.toEqual({error: 'bad request'});
	});
});
