/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetProcessDefinitionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';

type StatisticsFilter = NonNullable<GetProcessDefinitionStatisticsRequestBody['filter']>;

/** Returns `undefined` when no instance state is selected — the caller should skip the request entirely. */
function getStatisticsFilter({
	active,
	incidents,
	completed,
	canceled,
}: {
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
}): StatisticsFilter | undefined {
	const clauses: StatisticsFilter[] = [];

	if (active && incidents) {
		clauses.push({state: {$eq: 'ACTIVE'}});
	} else if (active) {
		clauses.push({state: {$eq: 'ACTIVE'}, hasIncident: false});
	} else if (incidents) {
		clauses.push({state: {$eq: 'ACTIVE'}, hasIncident: true});
	}

	if (completed) {
		clauses.push({state: {$eq: 'COMPLETED'}});
	}

	if (canceled) {
		clauses.push({state: {$eq: 'TERMINATED'}});
	}

	if (clauses.length === 0) {
		return undefined;
	}

	if (clauses.length === 1) {
		return clauses[0]!;
	}

	return {$or: clauses};
}

export {getStatisticsFilter};
