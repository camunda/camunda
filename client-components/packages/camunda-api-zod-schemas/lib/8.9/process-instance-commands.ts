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

const variableInstructionSchema = z.object({
	variables: z.record(z.string(), z.unknown()),
	scopeId: z.string().optional(),
});
const activateInstructionSchema = z.object({
	elementId: z.string(),
	variableInstructions: z.array(variableInstructionSchema).optional(),
	ancestorElementInstanceKey: z.string().optional(),
});
const moveInstructionSchema = z.object({
	sourceElementInstruction: z.discriminatedUnion('sourceType', [
		z.object({sourceType: z.literal('byId'), sourceElementId: z.string()}),
		z.object({sourceType: z.literal('byKey'), sourceElementInstanceKey: z.string()}),
	]),
	targetElementId: z.string(),
	ancestorScopeInstruction: z
		.discriminatedUnion('ancestorScopeType', [
			z.object({ancestorScopeType: z.literal('direct'), ancestorElementInstanceKey: z.string()}),
			z.object({ancestorScopeType: z.literal('inferred')}),
			z.object({ancestorScopeType: z.literal('sourceParent')}),
		])
		.optional(),
	variableInstructions: z.array(variableInstructionSchema).optional(),
});
const terminateInstructionSchema = z.union([
	z.object({elementId: z.string()}).strict(),
	z.object({elementInstanceKey: z.string()}).strict(),
]);

const modifyProcessInstanceRequestBodySchema = z
	.object({
		operationReference: z.number().optional(),
		activateInstructions: z.array(activateInstructionSchema).optional(),
		moveInstructions: z.array(moveInstructionSchema).optional(),
		terminateInstructions: z.array(terminateInstructionSchema).optional(),
	})
	.refine(
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
