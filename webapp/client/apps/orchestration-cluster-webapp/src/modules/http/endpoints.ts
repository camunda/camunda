/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints as unifiedAPIEndpoints} from '@camunda/camunda-api-zod-schemas/8.10';
import {getBootConfig} from '#/modules/config/getBootConfig';
import {mergePathname} from './mergePathname';

const BASE_REQUEST_OPTIONS: RequestInit = {
	credentials: 'include',
	mode: 'cors',
};

function getFullURL(url: string) {
	if (typeof window.location.origin !== 'string') {
		throw new Error('window.location.origin is not set');
	}

	return new URL(mergePathname(getBootConfig().contextPath, url), window.location.origin);
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
		new Request(getFullURL(unifiedAPIEndpoints.getCurrentUser.getUrl()), {
			...BASE_REQUEST_OPTIONS,
			method: unifiedAPIEndpoints.getCurrentUser.method,
			headers: {'Content-Type': 'application/json'},
		}),

	getSystemConfiguration: () =>
		new Request(getFullURL(unifiedAPIEndpoints.getSystemConfiguration.getUrl()), {
			...BASE_REQUEST_OPTIONS,
			method: unifiedAPIEndpoints.getSystemConfiguration.method,
			headers: {'Content-Type': 'application/json'},
		}),
};

export {endpoints};
