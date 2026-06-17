/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, delay as mswDelay, type RequestHandler} from 'msw';
import type {z} from 'zod';

type RequestMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type PayloadMethod = 'POST' | 'PUT' | 'PATCH';
type Path = Parameters<typeof http.get>[0];

type CreateEndpointMockParams<Method extends RequestMethod> = {
	endpoint: Path;
	method: Method | (string & {});
};

type SuccessMockParams = {
	successResponse: Response;
	delay?: number;
};

type PayloadMockParams<Schema extends z.ZodType> = {
	schema: Schema;
	failureResponse: Response;
	successResponse: Response;
	delay?: number;
};

type PayloadMock = {
	<Schema extends z.ZodType>(params: PayloadMockParams<Schema>): RequestHandler;
	(params: SuccessMockParams): RequestHandler;
};

type MockEndpoint<Method extends RequestMethod> = Method extends PayloadMethod
	? PayloadMock
	: (params: SuccessMockParams) => RequestHandler;

function createEndpointMock<Method extends RequestMethod>({
	endpoint,
	method,
}: CreateEndpointMockParams<Method>): MockEndpoint<Method> {
	return (params: SuccessMockParams | PayloadMockParams<z.ZodType>) => {
		const resolver = async ({request}: {request: Request}) => {
			if (params.delay) {
				await mswDelay(params.delay);
			}

			if (!('schema' in params)) {
				return params.successResponse.clone();
			}

			try {
				const payload = await request.json();
				const result = params.schema.safeParse(payload);

				return result.success ? params.successResponse.clone() : params.failureResponse.clone();
			} catch {
				return params.failureResponse.clone();
			}
		};

		switch (method) {
			case 'POST':
				return http.post(endpoint, resolver);
			case 'PUT':
				return http.put(endpoint, resolver);
			case 'PATCH':
				return http.patch(endpoint, resolver);
			case 'DELETE':
				return http.delete(endpoint, resolver);
			case 'GET':
			default:
				return http.get(endpoint, resolver);
		}
	};
}

type SequentialMockValidation<Schema extends z.ZodType> = {
	schema: Schema;
	failureResponse: Response;
};

function createSequentialEndpointMock<Method extends RequestMethod>({
	endpoint,
	method,
}: CreateEndpointMockParams<Method>): <Schema extends z.ZodType>(
	responses: Response[],
	validation?: SequentialMockValidation<Schema>,
) => RequestHandler {
	return (responses, validation) => {
		let callIndex = 0;
		const resolver = async ({request}: {request: Request}) => {
			if (validation) {
				try {
					const payload = await request.json();
					const result = validation.schema.safeParse(payload);
					if (!result.success) return validation.failureResponse.clone();
				} catch {
					return validation.failureResponse.clone();
				}
			}
			const response = responses[Math.min(callIndex, responses.length - 1)]!;
			callIndex++;
			return response.clone();
		};

		switch (method) {
			case 'POST':
				return http.post(endpoint, resolver);
			case 'PUT':
				return http.put(endpoint, resolver);
			case 'PATCH':
				return http.patch(endpoint, resolver);
			case 'DELETE':
				return http.delete(endpoint, resolver);
			case 'GET':
			default:
				return http.get(endpoint, resolver);
		}
	};
}

export {createEndpointMock, createSequentialEndpointMock};
