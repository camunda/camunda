/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {type ProcessInstance} from '../8.8';

const deleteProcessInstanceRequestBodySchema = z
	.object({
		operationReference: z.number().int(),
	})
	.optional();
type DeleteProcessInstanceRequestBody = z.infer<typeof deleteProcessInstanceRequestBodySchema>;

const deleteProcessInstance: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/deletion`,
};

export {deleteProcessInstanceRequestBodySchema, deleteProcessInstance};
export type {DeleteProcessInstanceRequestBody};
