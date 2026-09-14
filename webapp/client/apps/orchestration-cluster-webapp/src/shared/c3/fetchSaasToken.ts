/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints} from '#/shared/http/endpoints';

async function fetchSaasToken(): Promise<string> {
	try {
		const response = await fetch(endpoints.getSaasUserToken());

		if (!response.ok) {
			console.error('Failed to fetch SaaS user token', response.status);
			return '';
		}

		return response.json();
	} catch (error) {
		console.error('Failed to fetch SaaS user token', error);
		return '';
	}
}

export {fetchSaasToken};
