/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	advancedDateTimeFilterSchema,
	advancedIntegerFilterSchema,
	advancedStringFilterSchema,
	API_VERSION,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from './common';
import {variableSchema} from './variable';

const userTaskVariableFilterSchema = variableSchema.pick({
	name: true,
	value: true,
});

const userTaskStateSchema = z.enum([
	'CREATING',
	'CREATED',
	'ASSIGNING',
	'UPDATING',
	'COMPLETING',
	'COMPLETED',
	'CANCELING',
	'CANCELED',
	'FAILED',
]);

type UserTaskState = z.infer<typeof userTaskStateSchema>;

const userTaskSchema = z.object({
	state: userTaskStateSchema,
	processDefinitionVersion: z.number(),
	processDefinitionId: z.string(),
	processName: z.string().nullable(),
	processInstanceKey: z.string(),
	rootProcessInstanceKey: z.string().nullable(),
	processDefinitionKey: z.string(),
	name: z.string().nullable(),
	elementId: z.string(),
	elementInstanceKey: z.string(),
	tenantId: z.string(),
	userTaskKey: z.string(),
	assignee: z.string().nullable(),
	candidateGroups: z.array(z.string()),
	candidateUsers: z.array(z.string()),
	dueDate: z.string().nullable(),
	followUpDate: z.string().nullable(),
	creationDate: z.string(),
	completionDate: z.string().nullable(),
	customHeaders: z.record(z.string(), z.unknown()).nullable(),
	formKey: z.string().nullable(),
	externalFormReference: z.string().nullable(),
	tags: z.array(z.string()),
	priority: z.number().int().min(0).max(100),
});
type UserTask = z.infer<typeof userTaskSchema>;

const getUserTask: Endpoint<Pick<UserTask, 'userTaskKey'>> = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}`,
};

const queryUserTasksRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['creationDate', 'completionDate', 'followUpDate', 'dueDate', 'priority'] as const,
	filter: userTaskSchema
		.pick({
			state: true,
			elementId: true,
			tenantId: true,
			processDefinitionId: true,
			userTaskKey: true,
			processDefinitionKey: true,
			processInstanceKey: true,
			elementInstanceKey: true,
		})
		.extend({
			assignee: advancedStringFilterSchema,
			priority: advancedIntegerFilterSchema,
			candidateGroup: advancedStringFilterSchema,
			candidateUser: advancedStringFilterSchema,
			creationDate: advancedDateTimeFilterSchema,
			completionDate: advancedDateTimeFilterSchema,
			followUpDate: advancedDateTimeFilterSchema,
			dueDate: advancedDateTimeFilterSchema,
			localVariables: z.array(userTaskVariableFilterSchema),
			processInstanceVariables: z.array(userTaskVariableFilterSchema),
			state: getEnumFilterSchema(userTaskStateSchema),
		})
		.partial(),
});
type QueryUserTasksRequestBody = z.infer<typeof queryUserTasksRequestBodySchema>;

const queryUserTasksResponseBodySchema = getQueryResponseBodySchema(userTaskSchema);
type QueryUserTasksResponseBody = z.infer<typeof queryUserTasksResponseBodySchema>;

const queryUserTasks: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/user-tasks/search`,
};

const formSchema = z.object({
	formKey: z.string(),
	tenantId: z.string(),
	schema: z.string(),
	version: z.number(),
});
type Form = z.infer<typeof formSchema>;

const getUserTaskForm: Endpoint<Pick<UserTask, 'userTaskKey'>> = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/form`,
};

const updateUserTaskRequestBodySchema = z.object({
	changeset: userTaskSchema
		.pick({
			dueDate: true,
			followUpDate: true,
			candidateUsers: true,
			candidateGroups: true,
			priority: true,
		})
		.partial(),
	action: z.string().optional(),
});
type UpdateUserTaskRequestBody = z.infer<typeof updateUserTaskRequestBodySchema>;

const updateUserTask: Endpoint<Pick<UserTask, 'userTaskKey'>> = {
	method: 'PATCH',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}`,
};

const getTask: Endpoint<Pick<UserTask, 'userTaskKey'>> = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}`,
};

const assignTaskRequestBodySchema = z.object({
	assignee: z.string(),
	allowOverride: z.boolean().optional(),
	action: z.string().optional(),
});
type AssignTaskRequestBody = z.infer<typeof assignTaskRequestBodySchema>;

const assignTask: Endpoint<Pick<UserTask, 'userTaskKey'>> = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignment`,
};

const unassignTaskRequestBodySchema = z.object({
	action: z.string().optional(),
});
type UnassignTaskRequestBody = z.infer<typeof unassignTaskRequestBodySchema>;

const unassignTask: Endpoint<Pick<UserTask, 'userTaskKey'>> = {
	method: 'DELETE',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignee`,
};

const completeTaskRequestBodySchema = z.object({
	variables: z.record(z.string(), z.unknown()),
	action: z.string().optional(),
});
type CompleteTaskRequestBody = z.infer<typeof completeTaskRequestBodySchema>;

const completeTask: Endpoint<Pick<UserTask, 'userTaskKey'>> = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/completion`,
};

const queryVariablesByUserTaskRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['name', 'value', 'fullValue', 'tenantId', 'variableKey', 'scopeKey', 'processInstanceKey'] as const,
	filter: z.object({
		name: advancedStringFilterSchema.optional(),
	}),
});
type QueryVariablesByUserTaskRequestBody = z.infer<typeof queryVariablesByUserTaskRequestBodySchema>;

const queryVariablesByUserTaskResponseBodySchema = getQueryResponseBodySchema(variableSchema);
type QueryVariablesByUserTaskResponseBody = z.infer<typeof queryVariablesByUserTaskResponseBodySchema>;

const queryVariablesByUserTask: Endpoint<Pick<UserTask, 'userTaskKey'> & {truncateValues?: boolean}> = {
	method: 'POST',
	getUrl: ({userTaskKey, truncateValues}) =>
		`/${API_VERSION}/user-tasks/${userTaskKey}/variables/search${truncateValues !== undefined ? `?truncateValues=${truncateValues}` : ''}`,
};

export {
	getUserTask,
	queryUserTasks,
	getUserTaskForm,
	getTask,
	assignTask,
	unassignTask,
	completeTask,
	queryVariablesByUserTask,
	userTaskSchema,
	userTaskStateSchema,
	queryUserTasksResponseBodySchema,
	queryUserTasksRequestBodySchema,
	formSchema,
	assignTaskRequestBodySchema,
	unassignTaskRequestBodySchema,
	completeTaskRequestBodySchema,
	queryVariablesByUserTaskRequestBodySchema,
	queryVariablesByUserTaskResponseBodySchema,
	updateUserTask,
	updateUserTaskRequestBodySchema,
};
export type {
	UserTask,
	QueryUserTasksResponseBody,
	QueryUserTasksRequestBody,
	Form,
	AssignTaskRequestBody,
	UnassignTaskRequestBody,
	CompleteTaskRequestBody,
	QueryVariablesByUserTaskRequestBody,
	QueryVariablesByUserTaskResponseBody,
	UpdateUserTaskRequestBody,
	UserTaskState,
};
