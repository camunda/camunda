/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';
import {type ProcessInstance} from './processes';
import {deleteProcessInstanceRequestSchema, processInstanceModificationInstructionSchema} from './gen';

const deleteProcessInstanceRequestBodySchema = deleteProcessInstanceRequestSchema;
type DeleteProcessInstanceRequestBody = z.infer<typeof deleteProcessInstanceRequestBodySchema>;

const deleteProcessInstance: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/deletion`,
};

const modifyProcessInstanceRequestBodySchema = processInstanceModificationInstructionSchema.refine(
	({activateInstructions, moveInstructions, terminateInstructions}) =>
		(activateInstructions !== undefined && activateInstructions.length > 0) ||
		(moveInstructions !== undefined && moveInstructions.length > 0) ||
		(terminateInstructions !== undefined && terminateInstructions.length > 0),
	{
		message:
			'At least one instruction (activateInstructions, moveInstructions, or terminateInstructions) must be provided with at least one element',
	},
);
type ModifyProcessInstanceRequestBody = z.infer<typeof modifyProcessInstanceRequestBodySchema>;

const modifyProcessInstance: Endpoint<Pick<ProcessInstance, 'processInstanceKey'>> = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/modification`,
};

export {
	deleteProcessInstanceRequestBodySchema,
	deleteProcessInstance,
	modifyProcessInstanceRequestBodySchema,
	modifyProcessInstance,
};
export type {DeleteProcessInstanceRequestBody, ModifyProcessInstanceRequestBody};
