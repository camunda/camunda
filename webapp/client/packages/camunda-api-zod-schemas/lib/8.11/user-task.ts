/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {formResultSchema} from './gen/zod/formResultSchema';
import {userTaskAssignmentRequestSchema} from './gen/zod/userTaskAssignmentRequestSchema';
import {userTaskCompletionRequestSchema} from './gen/zod/userTaskCompletionRequestSchema';
import {userTaskEffectiveVariableSearchQueryRequestSchema} from './gen/zod/userTaskEffectiveVariableSearchQueryRequestSchema';
import {userTaskResultSchema} from './gen/zod/userTaskResultSchema';
import {userTaskSearchQueryResultSchema} from './gen/zod/userTaskSearchQueryResultSchema';
import {userTaskSearchQuerySchema} from './gen/zod/userTaskSearchQuerySchema';
import {userTaskStateEnumSchema} from './gen/zod/userTaskStateEnumSchema';
import {userTaskUpdateRequestSchema} from './gen/zod/userTaskUpdateRequestSchema';
import {variableSearchQueryResultSchema} from './gen/zod/variableSearchQueryResultSchema';
import type {FormResult} from './gen/types/FormResult';
import type {UserTaskAssignmentRequest} from './gen/types/UserTaskAssignmentRequest';
import type {UserTaskCompletionRequest} from './gen/types/UserTaskCompletionRequest';
import type {UserTaskEffectiveVariableSearchQueryRequest} from './gen/types/UserTaskEffectiveVariableSearchQueryRequest';
import type {UserTaskResult} from './gen/types/UserTaskResult';
import type {UserTaskSearchQuery} from './gen/types/UserTaskSearchQuery';
import type {UserTaskSearchQueryResult} from './gen/types/UserTaskSearchQueryResult';
import type {UserTaskStateEnumKey} from './gen/types/UserTaskStateEnum';
import type {UserTaskUpdateRequest} from './gen/types/UserTaskUpdateRequest';
import type {VariableSearchQueryResult} from './gen/types/VariableSearchQueryResult';

const userTaskStateSchema = userTaskStateEnumSchema;
type UserTaskState = UserTaskStateEnumKey;

const userTaskSchema = userTaskResultSchema;
type UserTask = UserTaskResult;

const queryUserTasksRequestBodySchema = userTaskSearchQuerySchema;
type QueryUserTasksRequestBody = UserTaskSearchQuery;

const queryUserTasksResponseBodySchema = userTaskSearchQueryResultSchema;
type QueryUserTasksResponseBody = UserTaskSearchQueryResult;

const formSchema = formResultSchema;
type Form = FormResult;

const updateUserTaskRequestBodySchema = userTaskUpdateRequestSchema;
type UpdateUserTaskRequestBody = UserTaskUpdateRequest;

const assignTaskRequestBodySchema = userTaskAssignmentRequestSchema;
type AssignTaskRequestBody = UserTaskAssignmentRequest;

const completeTaskRequestBodySchema = userTaskCompletionRequestSchema;
type CompleteTaskRequestBody = UserTaskCompletionRequest;

const queryVariablesByUserTaskRequestBodySchema = userTaskEffectiveVariableSearchQueryRequestSchema;
type QueryVariablesByUserTaskRequestBody = UserTaskEffectiveVariableSearchQueryRequest;

const queryVariablesByUserTaskResponseBodySchema = variableSearchQueryResultSchema;
type QueryVariablesByUserTaskResponseBody = VariableSearchQueryResult;

const getUserTask = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const queryUserTasks = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/user-tasks/search` as const,
} as const satisfies Endpoint;

const getUserTaskForm = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/form` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const updateUserTask = {
	method: 'PATCH',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const getTask = {
	method: 'GET',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const assignTask = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignment` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const unassignTask = {
	method: 'DELETE',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/assignee` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

const completeTask = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/completion` as const,
} as const satisfies Endpoint<Pick<UserTask, 'userTaskKey'>>;

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
	CompleteTaskRequestBody,
	QueryVariablesByUserTaskRequestBody,
	QueryVariablesByUserTaskResponseBody,
	UpdateUserTaskRequestBody,
	UserTaskState,
};
