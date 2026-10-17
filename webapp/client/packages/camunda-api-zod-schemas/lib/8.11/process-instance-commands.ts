/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {deleteProcessInstanceRequestSchema} from './gen/zod/deleteProcessInstanceRequestSchema';
import {processInstanceModificationInstructionSchema} from './gen/zod/processInstanceModificationInstructionSchema';
import type {DeleteProcessInstanceRequest} from './gen/types/DeleteProcessInstanceRequest';
import type {ProcessInstanceModificationInstruction} from './gen/types/ProcessInstanceModificationInstruction';
import {type ProcessInstance} from './processes';

const deleteProcessInstanceRequestBodySchema = deleteProcessInstanceRequestSchema;
type DeleteProcessInstanceRequestBody = DeleteProcessInstanceRequest;

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
type ModifyProcessInstanceRequestBody = ProcessInstanceModificationInstruction;

const deleteProcessInstance = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/deletion` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

const modifyProcessInstance = {
	method: 'POST',
	getUrl: ({processInstanceKey}) => `/${API_VERSION}/process-instances/${processInstanceKey}/modification` as const,
} as const satisfies Endpoint<Pick<ProcessInstance, 'processInstanceKey'>>;

export {
	deleteProcessInstanceRequestBodySchema,
	deleteProcessInstance,
	modifyProcessInstanceRequestBodySchema,
	modifyProcessInstance,
};

export type {DeleteProcessInstanceRequestBody, ModifyProcessInstanceRequestBody};
