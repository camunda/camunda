/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CallHierarchy} from '@camunda/camunda-api-zod-schemas/8.10';

function createCallHierarchy(overrides?: Partial<CallHierarchy>): CallHierarchy {
	return {
		processInstanceKey: '2251799813685252',
		processDefinitionKey: '2251799813685251',
		processDefinitionName: 'Root Process',
		...overrides,
	};
}

export {createCallHierarchy};
