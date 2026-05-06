/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getCurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';

const BASE_REQUEST_OPTIONS: RequestInit = {
	credentials: 'include',
	mode: 'cors',
};

// TODO: prepend getClientConfig().contextPath once getClientConfig is supported https://github.com/camunda/camunda/issues/51322
function getFullURL(path: string) {
	return new URL(path, window.location.origin);
}

const endpoints = {
	login: (body: {username: string; password: string}) =>
		new Request(getFullURL('/login'), {
			...BASE_REQUEST_OPTIONS,
			method: 'POST',
			body: new URLSearchParams(body).toString(),
			headers: {
				'Content-Type': 'application/x-www-form-urlencoded',
			},
		}),

	logout: () =>
		new Request(getFullURL('/logout'), {
			...BASE_REQUEST_OPTIONS,
			method: 'POST',
		}),

	getCurrentUser: () =>
		new Request(getFullURL(getCurrentUser.getUrl()), {
			...BASE_REQUEST_OPTIONS,
			method: getCurrentUser.method,
			headers: {'Content-Type': 'application/json'},
		}),
};

export {endpoints};
