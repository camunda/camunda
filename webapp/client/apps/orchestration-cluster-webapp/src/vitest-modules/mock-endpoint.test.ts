/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {z} from 'zod';
import {createEndpointMock, createResponseGate} from '#/shared-test-modules/mock-endpoint';
import {it} from './test-extend';
import {describe, expect} from 'vitest';

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

	it('should match fixed responses by request body without consuming an unmatched one-time response', async ({
		worker,
	}) => {
		worker.use(
			mockDataEndpoint({
				requestBody: z.object({key: z.literal('selected')}),
				successResponse: HttpResponse.json({data: 'selected'}),
				once: true,
			}),
			mockDataEndpoint({successResponse: HttpResponse.json({data: 'default'})}),
		);

		await expect((await postData({key: 'other'})).json()).resolves.toEqual({data: 'default'});
		await expect((await postData({key: 'selected'})).json()).resolves.toEqual({data: 'selected'});
		await expect((await postData({key: 'selected'})).json()).resolves.toEqual({data: 'default'});
	});

	it('should hold a response until released and allow subsequent requests to finish', async ({worker}) => {
		const responseGate = createResponseGate(worker);
		worker.use(
			mockDataEndpoint({
				successResponse: HttpResponse.json({data: 'value'}),
				delay: responseGate.delay,
			}),
		);
		const response = postData({key: 'value'});

		try {
			await responseGate.requested;
		} finally {
			await responseGate.release();
		}

		await expect((await response).json()).resolves.toEqual({data: 'value'});
		await expect((await postData({key: 'value'})).json()).resolves.toEqual({data: 'value'});
	});

	it('should release every concurrent response before cleanup', async ({worker}) => {
		const responseGate = createResponseGate(worker);
		worker.use(
			mockDataEndpoint({
				successResponse: HttpResponse.json({data: 'value'}),
				delay: responseGate.delay,
			}),
		);
		const responses = [postData({key: 'first'}), postData({key: 'second'})];

		try {
			await responseGate.requested;
		} finally {
			await responseGate.release();
		}

		await expect(Promise.all(responses.map(async (response) => (await response).json()))).resolves.toEqual([
			{data: 'value'},
			{data: 'value'},
		]);
	});
});
