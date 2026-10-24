/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {reactQueryClient} from './reactQueryClient';
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
	} catch (error) {
		// The request that needs the token reports the failure to the user, which looks like a
		// rejected login. Log the cause so a broken token request is distinguishable from that.
		console.error('Failed to fetch a CSRF token', error);
	}
}

type RequestOptions = {
	skipSessionCheck?: boolean;
	/**
	 * Tells if a successful response still means that the caller has no session, so it is handled
	 * like a 401. An unprotected API answers the current user endpoint this way.
	 */
	isUnauthenticated?: (response: Response) => Promise<boolean>;
};

async function request(
	input: RequestInfo,
	{skipSessionCheck = false, isUnauthenticated}: RequestOptions = {},
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
		const isUnauthorized =
			response.status === 401 || (response.ok && (await isUnauthenticated?.(response.clone())) === true);

		if (response.ok && !isUnauthorized) {
			authenticationStore.activateSession();
		}

		storeCsrfTokenFromResponse(response);

		if (!skipSessionCheck && isUnauthorized) {
			authenticationStore.disableSession();
			reactQueryClient.clear();
		}

		if (response.ok && !isUnauthorized) {
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
