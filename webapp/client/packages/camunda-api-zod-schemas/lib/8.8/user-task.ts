/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	userTaskResultSchema,
	userTaskStateEnumSchema,
	userTaskSearchQuerySchema,
	userTaskSearchQueryResultSchema,
	formResultSchema,
	userTaskUpdateRequestSchema,
	userTaskAssignmentRequestSchema,
	userTaskCompletionRequestSchema,
	userTaskVariableSearchQueryRequestSchema,
	searchUserTaskVariables200Schema,
} from './gen';

const userTaskStateSchema = userTaskStateEnumSchema;
type UserTaskState = z.infer<typeof userTaskStateSchema>;

const userTaskSchema = userTaskResultSchema;
type UserTask = z.infer<typeof userTaskSchema>;

const getUserTask = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const queryUserTasksRequestBodySchema = userTaskSearchQuerySchema;
type QueryUserTasksRequestBody = z.infer<typeof queryUserTasksRequestBodySchema>;

const queryUserTasksResponseBodySchema = userTaskSearchQueryResultSchema;
type QueryUserTasksResponseBody = z.infer<typeof queryUserTasksResponseBodySchema>;

const queryUserTasks = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/user-tasks/search` as const,
} as const satisfies Endpoint;

const formSchema = formResultSchema;
type Form = z.infer<typeof formSchema>;

const getUserTaskForm = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/form` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const updateUserTaskRequestBodySchema = userTaskUpdateRequestSchema;
type UpdateUserTaskRequestBody = z.infer<typeof updateUserTaskRequestBodySchema>;

const updateUserTask = {
	method: 'PATCH',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const getTask = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const assignTaskRequestBodySchema = userTaskAssignmentRequestSchema;
type AssignTaskRequestBody = z.infer<typeof assignTaskRequestBodySchema>;

const assignTask = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignment` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const unassignTaskRequestBodySchema = z.object({
	action: z.string().optional(),
});
type UnassignTaskRequestBody = z.infer<typeof unassignTaskRequestBodySchema>;

const unassignTask = {
	method: 'DELETE',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignee` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const completeTaskRequestBodySchema = userTaskCompletionRequestSchema;
type CompleteTaskRequestBody = z.infer<typeof completeTaskRequestBodySchema>;

const completeTask = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/completion` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const queryVariablesByUserTaskRequestBodySchema = userTaskVariableSearchQueryRequestSchema;
type QueryVariablesByUserTaskRequestBody = z.infer<typeof queryVariablesByUserTaskRequestBodySchema>;

const queryVariablesByUserTaskResponseBodySchema = searchUserTaskVariables200Schema;
type QueryVariablesByUserTaskResponseBody = z.infer<typeof queryVariablesByUserTaskResponseBodySchema>;

const queryVariablesByUserTask = {
	method: 'POST',
	getUrl: ({userTaskKey, truncateValues}) =>
		`/${API_VERSION}/user-tasks/${userTaskKey}/effective-variables/search${truncateValues !== undefined ? `?truncateValues=${truncateValues}` : ''}` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'> & {truncateValues?: boolean}>;

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
