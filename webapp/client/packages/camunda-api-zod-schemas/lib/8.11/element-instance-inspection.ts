/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {conditionWaitStateDetailsSchema} from './gen/zod/conditionWaitStateDetailsSchema';
import {elementInstanceWaitStateQueryResultSchema} from './gen/zod/elementInstanceWaitStateQueryResultSchema';
import {elementInstanceWaitStateQuerySchema} from './gen/zod/elementInstanceWaitStateQuerySchema';
import {elementInstanceWaitStateResultSchema} from './gen/zod/elementInstanceWaitStateResultSchema';
import {jobWaitStateDetailsSchema} from './gen/zod/jobWaitStateDetailsSchema';
import {messageWaitStateDetailsSchema} from './gen/zod/messageWaitStateDetailsSchema';
import {signalWaitStateDetailsSchema} from './gen/zod/signalWaitStateDetailsSchema';
import {timerWaitStateDetailsSchema} from './gen/zod/timerWaitStateDetailsSchema';
import {userTaskWaitStateDetailsSchema} from './gen/zod/userTaskWaitStateDetailsSchema';
import {waitStateDetailsSchema} from './gen/zod/waitStateDetailsSchema';
import {waitStateElementTypeEnumSchema} from './gen/zod/waitStateElementTypeEnumSchema';
import {waitStateTypeEnumSchema} from './gen/zod/waitStateTypeEnumSchema';
import type {ElementInstanceWaitStateQuery} from './gen/types/ElementInstanceWaitStateQuery';
import type {ElementInstanceWaitStateQueryResult} from './gen/types/ElementInstanceWaitStateQueryResult';
import type {ElementInstanceWaitStateResult} from './gen/types/ElementInstanceWaitStateResult';
import type {WaitStateDetails} from './gen/types/WaitStateDetails';
import type {WaitStateElementTypeEnumKey} from './gen/types/WaitStateElementTypeEnum';
import type {WaitStateTypeEnumKey} from './gen/types/WaitStateTypeEnum';

const waitStateTypeSchema = waitStateTypeEnumSchema;
type WaitStateType = WaitStateTypeEnumKey;

const waitStateElementTypeSchema = waitStateElementTypeEnumSchema;
type WaitStateElementType = WaitStateElementTypeEnumKey;

// The gen variant types have `waitStateType: string`. The discriminated union narrows each variant to its literal.
type JobWaitStateDetails = Extract<WaitStateDetails, {waitStateType: 'JOB'}>;
type MessageWaitStateDetails = Extract<WaitStateDetails, {waitStateType: 'MESSAGE'}>;
type UserTaskWaitStateDetails = Extract<WaitStateDetails, {waitStateType: 'USER_TASK'}>;
type TimerWaitStateDetails = Extract<WaitStateDetails, {waitStateType: 'TIMER'}>;
type SignalWaitStateDetails = Extract<WaitStateDetails, {waitStateType: 'SIGNAL'}>;
type ConditionWaitStateDetails = Extract<WaitStateDetails, {waitStateType: 'CONDITION'}>;

const elementInstanceInspectionSchema = elementInstanceWaitStateResultSchema;
type ElementInstanceInspection = ElementInstanceWaitStateResult;

const queryElementInstanceInspectionRequestBodySchema = elementInstanceWaitStateQuerySchema;
type QueryElementInstanceInspectionRequestBody = ElementInstanceWaitStateQuery;

const queryElementInstanceInspectionResponseBodySchema = elementInstanceWaitStateQueryResultSchema;
type QueryElementInstanceInspectionResponseBody = ElementInstanceWaitStateQueryResult;

const queryElementInstanceInspection = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/element-instances/wait-states/search` as const;
	},
} as const satisfies Endpoint;

export {
	waitStateTypeSchema,
	waitStateElementTypeSchema,
	waitStateDetailsSchema,
	jobWaitStateDetailsSchema,
	messageWaitStateDetailsSchema,
	userTaskWaitStateDetailsSchema,
	timerWaitStateDetailsSchema,
	signalWaitStateDetailsSchema,
	conditionWaitStateDetailsSchema,
	elementInstanceInspectionSchema,
	queryElementInstanceInspectionRequestBodySchema,
	queryElementInstanceInspectionResponseBodySchema,
	queryElementInstanceInspection,
};

export type {
	WaitStateType,
	WaitStateElementType,
	WaitStateDetails,
	JobWaitStateDetails,
	MessageWaitStateDetails,
	UserTaskWaitStateDetails,
	TimerWaitStateDetails,
	SignalWaitStateDetails,
	ConditionWaitStateDetails,
	ElementInstanceInspection,
	QueryElementInstanceInspectionRequestBody,
	QueryElementInstanceInspectionResponseBody,
};
