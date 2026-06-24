/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {prepareCustomFiltersParams} from './prepareCustomFiltersParams';
import {type CustomFilterSearchParams, type TasklistIndexSearch} from './searchSchema';

function getCustomFilterParams({username, filter}: {username: string; filter: string}): CustomFilterSearchParams {
	const stored = getStateLocally('tasklist.customFilters')?.[filter];

	return stored === undefined ? {} : prepareCustomFiltersParams(stored, username);
}

function getCustomFilterSearch(options: {
	currentSearch: TasklistIndexSearch;
	filter: string;
	username: string;
}): TasklistIndexSearch {
	const {currentSearch, filter, username} = options;
	const customFilterParams = getCustomFilterParams({username, filter});

	const clearedCriteria: CustomFilterSearchParams = {
		state: undefined,
		assigned: undefined,
		assignee: undefined,
		candidateGroup: undefined,
		processDefinitionKey: undefined,
		tenantId: undefined,
		dueDateFrom: undefined,
		dueDateTo: undefined,
		followUpDateFrom: undefined,
		followUpDateTo: undefined,
		elementId: undefined,
	};

	const sortBy = currentSearch.sortBy === 'completion' ? 'creation' : currentSearch.sortBy;

	return {
		...clearedCriteria,
		...customFilterParams,
		filter,
		sortBy,
	};
}

export {getCustomFilterSearch};
