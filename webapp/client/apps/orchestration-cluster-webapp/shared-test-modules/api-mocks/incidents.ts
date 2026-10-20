/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Incident} from '@camunda/camunda-api-zod-schemas/8.11';

function createIncident(overrides?: Partial<Incident>): Incident {
	return {
		incidentKey: '2251799813685290',
		processDefinitionId: 'my-process',
		processDefinitionKey: '2251799813685279',
		processInstanceKey: '2251799813685280',
		rootProcessInstanceKey: null,
		elementId: 'task-1',
		elementInstanceKey: '2251799813685281',
		errorType: 'JOB_NO_RETRIES',
		errorMessage: 'No more retries left.',
		creationTime: '2026-01-15T10:00:00.000Z',
		state: 'ACTIVE',
		jobKey: '2251799813685291',
		tenantId: '<default>',
		...overrides,
	};
}

export {createIncident};
