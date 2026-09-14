/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function getStage(host: string): 'dev' | 'int' | 'prod' | 'unknown' {
	const hostname = host.toLowerCase().split(':')[0] ?? '';

	if (hostname === 'dev.ultrawombat.com' || hostname.endsWith('.dev.ultrawombat.com')) {
		return 'dev';
	}

	if (hostname === 'ultrawombat.com' || hostname.endsWith('.ultrawombat.com')) {
		return 'int';
	}

	if (hostname === 'camunda.io' || hostname.endsWith('.camunda.io')) {
		return 'prod';
	}

	return 'unknown';
}

export {getStage};
