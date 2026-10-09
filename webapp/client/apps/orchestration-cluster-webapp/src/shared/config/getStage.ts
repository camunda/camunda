/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const DEV_DOMAIN = 'dev.ultrawombat.com';
const INT_DOMAIN = 'ultrawombat.com';
const PROD_DOMAIN = 'camunda.io';

function getStage(host: string): 'dev' | 'int' | 'prod' | 'unknown' {
	const hostname = host.toLowerCase().split(':')[0] ?? '';

	if (hostname === DEV_DOMAIN || hostname.endsWith(`.${DEV_DOMAIN}`)) {
		return 'dev';
	}

	if (hostname === INT_DOMAIN || hostname.endsWith(`.${INT_DOMAIN}`)) {
		return 'int';
	}

	if (hostname === PROD_DOMAIN || hostname.endsWith(`.${PROD_DOMAIN}`)) {
		return 'prod';
	}

	return 'unknown';
}

export {getStage};
