/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Form} from '@camunda/camunda-api-zod-schemas/8.11';
import {USER_TASK_FORM_SCHEMA} from './form-schemas';

function createUserTaskFormResponse(overrides?: Partial<Form>): Form {
	return {
		tenantId: '<default>',
		schema: USER_TASK_FORM_SCHEMA,
		version: 1,
		formKey: '2251799813685290',
		...overrides,
	};
}

export {createUserTaskFormResponse};
