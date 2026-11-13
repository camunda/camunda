/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseSearchWith, stringifySearchWith} from '@tanstack/react-router';
import {parseSearchValueSafe} from '#/shared/parseSearchValueSafe';

const LEGACY_PROCESS_SEARCH_PARAMS: Readonly<Record<string, string>> = {
	tenant: 'tenantId',
	ids: 'processInstanceKey',
	parentInstanceId: 'parentProcessInstanceKey',
	flowNodeId: 'elementId',
	operationId: 'batchOperationKey',
	batchOperationId: 'batchOperationKey',
	retriesLeft: 'hasRetriesLeft',
	startDateAfter: 'startDateFrom',
	startDateBefore: 'startDateTo',
	endDateAfter: 'endDateFrom',
	endDateBefore: 'endDateTo',
};

const STRING_PARAMS = new Set([
	'process',
	'processDefinitionId',
	'businessId',
	'errorMessage',
	'sort',
	'elementInstanceKey',
	'anchorElementId',
	'elementSearch',
	...Object.entries(LEGACY_PROCESS_SEARCH_PARAMS).flatMap(([legacyKey, key]) =>
		key === 'hasRetriesLeft' ? [] : [legacyKey, key],
	),
]);

type LegacyProcessLocation = {
	search: Record<string, unknown>;
};

function preserveProcessSearchStrings({url}: {url: URL}): URL {
	if (!/^\/operate\/(?:instances|processes)(?:\/|$)/.test(url.pathname)) {
		return url;
	}

	const search: Record<string, unknown> = parseSearchWith(parseSearchValueSafe)(url.search);
	for (const [key, value] of Object.entries(search)) {
		if (STRING_PARAMS.has(key) && typeof value !== 'string') {
			search[key] = url.searchParams.get(key);
		}
	}
	url.search = stringifySearchWith(JSON.stringify, parseSearchValueSafe)(search);
	return url;
}

function migrateLegacyProcessSearch({search}: LegacyProcessLocation): Record<string, unknown> | null {
	const legacyParams = Object.entries(LEGACY_PROCESS_SEARCH_PARAMS).filter(([legacyKey]) =>
		Object.hasOwn(search, legacyKey),
	);

	if (legacyParams.length === 0) {
		return null;
	}

	return legacyParams.reduce<Record<string, unknown>>(
		(migrated, [legacyKey, key]) => {
			const {[legacyKey]: value, ...rest} = migrated;
			return rest[key] === undefined ? {...rest, [key]: value} : rest;
		},
		{...search},
	);
}

export {migrateLegacyProcessSearch, preserveProcessSearchStrings};
