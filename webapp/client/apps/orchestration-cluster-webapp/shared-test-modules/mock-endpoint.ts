/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, type RequestHandler} from 'msw';
import type {z} from 'zod';

type RequestMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type PayloadMethod = 'POST' | 'PUT' | 'PATCH';
type Path = Parameters<typeof http.get>[0];

type CreateEndpointMockParams<Method extends RequestMethod> = {
	endpoint: Path;
	method: Method;
};

type SuccessMockParams = {
	successResponse: Response;
};

type PayloadMockParams<Schema extends z.ZodType> = {
	schema: Schema;
	failureResponse: Response;
	successResponse: Response;
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
			if (!('schema' in params)) {
				return params.successResponse;
			}

			try {
				const payload = await request.json();
				const result = params.schema.safeParse(payload);

				return result.success ? params.successResponse : params.failureResponse;
			} catch {
				return params.failureResponse;
			}
		};

		switch (method) {
			case 'GET':
				return http.get(endpoint, resolver);
			case 'POST':
				return http.post(endpoint, resolver);
			case 'PUT':
				return http.put(endpoint, resolver);
			case 'PATCH':
				return http.patch(endpoint, resolver);
			case 'DELETE':
				return http.delete(endpoint, resolver);
		}
	};
}

export {createEndpointMock};
