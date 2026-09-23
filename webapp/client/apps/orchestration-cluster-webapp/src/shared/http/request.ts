/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {reactQueryClient} from '#/shared/http/reactQueryClient';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {z} from 'zod';

type RequestError =
	| {
			variant: 'network-error';
			response: null;
			networkError: unknown;
	  }
	| {
			variant: 'failed-response';
			response: Response;
			networkError: null;
	  };

function getCsrfTokenFromStorage() {
	return sessionStorage.getItem('X-CSRF-TOKEN');
}

function storeCsrfTokenFromResponse(response: Response) {
	const tokenFromResponse = response.headers.get('X-CSRF-TOKEN');

	if (tokenFromResponse) {
		sessionStorage.setItem('X-CSRF-TOKEN', tokenFromResponse);
	}
}

/**
 * Gets a CSRF token and keeps it for the requests that follow. Does not change the session state,
 * because this request does not tell us if the user has a session.
 */
async function requestCsrfToken(input: Request) {
	try {
		storeCsrfTokenFromResponse(await fetch(input));
	} catch {
		// The request that needs the token reports the failure to the user.
	}
}

async function request(
	input: RequestInfo,
	{skipSessionCheck} = {skipSessionCheck: false},
): Promise<
	| {
			response: Response;
			error: null;
	  }
	| {
			response: null;
			error: RequestError;
	  }
> {
	try {
		const csrfToken = getCsrfTokenFromStorage();
		if (input instanceof Request) {
			const method = input.method;

			if (csrfToken && method && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())) {
				input.headers.append('X-CSRF-TOKEN', csrfToken);
			}
		}

		const response = await fetch(input);

		if (response.ok) {
			authenticationStore.activateSession();
		}

		storeCsrfTokenFromResponse(response);

		if (!skipSessionCheck && response.status === 401) {
			authenticationStore.disableSession();
			reactQueryClient.clear();
		}

		if (response.ok) {
			return {
				response,
				error: null,
			};
		}

		return {
			response: null,
			error: {
				response,
				networkError: null,
				variant: 'failed-response',
			},
		};
	} catch (error) {
		return {
			response: null,
			error: {
				response: null,
				networkError: error,
				variant: 'network-error',
			},
		};
	}
}

const requestErrorSchema = z.union([
	z.object({
		variant: z.literal('network-error'),
		response: z.literal(null),
		networkError: z.instanceof(Error),
	}),
	z.object({
		variant: z.literal('failed-response'),
		response: z.instanceof(Response),
		networkError: z.literal(null),
	}),
]);

export {getCsrfTokenFromStorage, request, requestCsrfToken, requestErrorSchema, type RequestError};
