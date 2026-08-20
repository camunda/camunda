/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser, QueryProcessDefinitionsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import type {ProcessesSearch} from '#/tasklist/modules/processes/searchSchema';

const PROCESS_DEFINITIONS_PAGE_SIZE = 12;

function getProcessDefinitionsRequestBody(
	search: ProcessesSearch,
	tenants: CurrentUser['tenants'],
): QueryProcessDefinitionsRequestBody {
	const tenantId = tenants.length > 1 ? (search.tenantId ?? tenants[0]?.tenantId) : tenants[0]?.tenantId;

	return {
		filter: {
			tenantId,
			processDefinitionId: search.search === undefined ? undefined : {$like: `*${search.search}*`},
			hasStartForm: search.hasStartForm === undefined ? undefined : search.hasStartForm === 'yes',
			isLatestVersion: true,
		},
		page: {limit: PROCESS_DEFINITIONS_PAGE_SIZE},
	};
}

export {getProcessDefinitionsRequestBody};
