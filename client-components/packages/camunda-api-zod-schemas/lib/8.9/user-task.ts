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
	userTaskAuditLogSearchQueryRequestSchema,
	auditLogSearchQueryResultSchema,
} from './gen';

const userTaskStateSchema = userTaskStateEnumSchema;
type UserTaskState = z.infer<typeof userTaskStateSchema>;

const userTaskSchema = userTaskResultSchema;
type UserTask = z.infer<typeof userTaskSchema>;

const getUserTask: Endpoint<{userTaskKey: string}> = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}`,
};

const queryUserTasksRequestBodySchema = userTaskSearchQuerySchema;
type QueryUserTasksRequestBody = z.infer<typeof queryUserTasksRequestBodySchema>;

const queryUserTasksResponseBodySchema = userTaskSearchQueryResultSchema;
type QueryUserTasksResponseBody = z.infer<typeof queryUserTasksResponseBodySchema>;

const queryUserTasks: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/user-tasks/search`,
};

const formSchema = formResultSchema;
type Form = z.infer<typeof formSchema>;

const getUserTaskForm: Endpoint<{userTaskKey: string}> = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/form`,
};

const updateUserTaskRequestBodySchema = userTaskUpdateRequestSchema;
type UpdateUserTaskRequestBody = z.infer<typeof updateUserTaskRequestBodySchema>;

const updateUserTask: Endpoint<{userTaskKey: string}> = {
	method: 'PATCH',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}`,
};

const getTask: Endpoint<{userTaskKey: string}> = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}`,
};

const assignTaskRequestBodySchema = userTaskAssignmentRequestSchema;
type AssignTaskRequestBody = z.infer<typeof assignTaskRequestBodySchema>;

const assignTask: Endpoint<{userTaskKey: string}> = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignment`,
};

const unassignTask: Endpoint<{userTaskKey: string}> = {
	method: 'DELETE',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignee`,
};

const completeTaskRequestBodySchema = userTaskCompletionRequestSchema;
type CompleteTaskRequestBody = z.infer<typeof completeTaskRequestBodySchema>;

const completeTask: Endpoint<{userTaskKey: string}> = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/completion`,
};

const queryVariablesByUserTaskRequestBodySchema = userTaskVariableSearchQueryRequestSchema;
type QueryVariablesByUserTaskRequestBody = z.infer<typeof queryVariablesByUserTaskRequestBodySchema>;

const queryVariablesByUserTaskResponseBodySchema = searchUserTaskVariables200Schema;
type QueryVariablesByUserTaskResponseBody = z.infer<typeof queryVariablesByUserTaskResponseBodySchema>;

const queryVariablesByUserTask: Endpoint<{userTaskKey: string; truncateValues?: boolean}> = {
	method: 'POST',
	getUrl: ({userTaskKey, truncateValues}) =>
		`/${API_VERSION}/user-tasks/${userTaskKey}/variables/search${truncateValues !== undefined ? `?truncateValues=${truncateValues}` : ''}`,
};

const queryUserTaskAuditLogsRequestBodySchema = userTaskAuditLogSearchQueryRequestSchema;
type QueryUserTaskAuditLogsRequestBody = z.infer<typeof queryUserTaskAuditLogsRequestBodySchema>;

const queryUserTaskAuditLogsResponseBodySchema = auditLogSearchQueryResultSchema;
type QueryUserTaskAuditLogsResponseBody = z.infer<typeof queryUserTaskAuditLogsResponseBodySchema>;

const queryUserTaskAuditLogs: Endpoint<{userTaskKey: string}> = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/audit-logs/search`,
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
	queryUserTaskAuditLogs,
	userTaskSchema,
	userTaskStateSchema,
	queryUserTasksResponseBodySchema,
	queryUserTasksRequestBodySchema,
	formSchema,
	assignTaskRequestBodySchema,
	completeTaskRequestBodySchema,
	queryVariablesByUserTaskRequestBodySchema,
	queryVariablesByUserTaskResponseBodySchema,
	queryUserTaskAuditLogsRequestBodySchema,
	queryUserTaskAuditLogsResponseBodySchema,
	updateUserTask,
	updateUserTaskRequestBodySchema,
};
export type {
	UserTask,
	QueryUserTasksResponseBody,
	QueryUserTasksRequestBody,
	Form,
	AssignTaskRequestBody,
	CompleteTaskRequestBody,
	QueryVariablesByUserTaskRequestBody,
	QueryVariablesByUserTaskResponseBody,
	QueryUserTaskAuditLogsRequestBody,
	QueryUserTaskAuditLogsResponseBody,
	UpdateUserTaskRequestBody,
	UserTaskState,
};
