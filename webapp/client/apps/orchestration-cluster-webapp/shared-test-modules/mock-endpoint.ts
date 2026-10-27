/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, delay as mswDelay, type DelayMode, type RequestHandler} from 'msw';
import type {SetupWorker} from 'msw/browser';
import type {z} from 'zod';

type RequestMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type PayloadMethod = 'POST' | 'PUT' | 'PATCH';
type Path = Parameters<typeof http.get>[0];
type ResponseDelay = DelayMode | number | ((requestId: string) => Promise<void>);

type CreateEndpointMockParams<Method extends RequestMethod> = {
	endpoint: Path;
	method: Method | (string & {});
};

type SuccessMockParams = {
	successResponse: Response;
	delay?: ResponseDelay;
	requestBody?: z.ZodType;
	/**
	 * Handle only the first matching request. Use carefully: our apps poll frequently,
	 * so a background poll may consume it before the intended request.
	 */
	once?: boolean;
};

type PayloadMockParams<Schema extends z.ZodType> = {
	schema: Schema;
	failureResponse: Response;
	successResponse: Response;
	delay?: ResponseDelay;
	requestBody?: z.ZodType;
	/**
	 * Handle only the first matching request. Use carefully: our apps poll frequently,
	 * so a background poll may consume it before the intended request.
	 */
	once?: boolean;
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
		const resolver = async ({request, requestId}: {request: Request; requestId: string}) => {
			if (typeof params.delay === 'function') {
				await params.delay(requestId);
			} else if (params.delay) {
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

		const options = {once: params.once ?? false};
		let handler: RequestHandler;

		switch (method) {
			case 'POST':
				handler = http.post(endpoint, resolver, options);
				break;
			case 'PUT':
				handler = http.put(endpoint, resolver, options);
				break;
			case 'PATCH':
				handler = http.patch(endpoint, resolver, options);
				break;
			case 'DELETE':
				handler = http.delete(endpoint, resolver, options);
				break;
			case 'GET':
			default:
				handler = http.get(endpoint, resolver, options);
		}

		const {requestBody} = params;
		if (requestBody !== undefined) {
			const matchesEndpoint = handler.predicate.bind(handler);
			handler.predicate = async (args) =>
				(await matchesEndpoint(args)) && requestBody.safeParse(await args.request.clone().json()).success;
		}

		return handler;
	};
}

function createResponseGate(worker: SetupWorker) {
	let releaseResponse!: () => void;
	let markRequested!: () => void;
	let markCompleted!: () => void;
	const pending = new Promise<void>((resolve) => {
		releaseResponse = resolve;
	});
	const requested = new Promise<void>((resolve) => {
		markRequested = resolve;
	});
	const completed = new Promise<void>((resolve) => {
		markCompleted = resolve;
	});
	let isReleased = false;
	const requests = new Set<string>();
	const onResponse = ({requestId}: {requestId: string}) => {
		if (requests.delete(requestId) && requests.size === 0) {
			markCompleted();
		}
	};
	worker.events.on('response:mocked', onResponse);

	return {
		requested,
		delay: (requestId: string) => {
			if (!isReleased) {
				requests.add(requestId);
			}
			markRequested();
			return pending;
		},
		release: async () => {
			isReleased = true;
			releaseResponse();
			try {
				if (requests.size > 0) {
					await completed;
				}
			} finally {
				worker.events.removeListener('response:mocked', onResponse);
			}
		},
	};
}

export {createEndpointMock, createResponseGate};
