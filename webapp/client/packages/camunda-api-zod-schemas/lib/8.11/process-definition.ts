/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {getProcessDefinitionXMLStatus200Schema} from './gen/zod/getProcessDefinitionXMLSchema';
import {getStartProcessFormStatus200Schema} from './gen/zod/getStartProcessFormSchema';
import {processDefinitionElementStatisticsQueryResultSchema} from './gen/zod/processDefinitionElementStatisticsQueryResultSchema';
import {processDefinitionElementStatisticsQuerySchema} from './gen/zod/processDefinitionElementStatisticsQuerySchema';
import {processDefinitionInstanceStatisticsQueryResultSchema} from './gen/zod/processDefinitionInstanceStatisticsQueryResultSchema';
import {processDefinitionInstanceStatisticsQuerySchema} from './gen/zod/processDefinitionInstanceStatisticsQuerySchema';
import {processDefinitionInstanceStatisticsResultSchema} from './gen/zod/processDefinitionInstanceStatisticsResultSchema';
import {processDefinitionInstanceVersionStatisticsQueryResultSchema} from './gen/zod/processDefinitionInstanceVersionStatisticsQueryResultSchema';
import {processDefinitionInstanceVersionStatisticsQuerySchema} from './gen/zod/processDefinitionInstanceVersionStatisticsQuerySchema';
import {processDefinitionInstanceVersionStatisticsResultSchema} from './gen/zod/processDefinitionInstanceVersionStatisticsResultSchema';
import {processDefinitionResultSchema} from './gen/zod/processDefinitionResultSchema';
import {processDefinitionSearchQueryResultSchema} from './gen/zod/processDefinitionSearchQueryResultSchema';
import {processDefinitionSearchQuerySchema} from './gen/zod/processDefinitionSearchQuerySchema';
import type {GetProcessDefinitionXMLStatus200} from './gen/types/GetProcessDefinitionXML';
import type {GetStartProcessFormStatus200} from './gen/types/GetStartProcessForm';
import type {ProcessDefinitionElementStatisticsQuery} from './gen/types/ProcessDefinitionElementStatisticsQuery';
import type {ProcessDefinitionElementStatisticsQueryResult} from './gen/types/ProcessDefinitionElementStatisticsQueryResult';
import type {ProcessDefinitionInstanceStatisticsQuery} from './gen/types/ProcessDefinitionInstanceStatisticsQuery';
import type {ProcessDefinitionInstanceStatisticsQueryResult} from './gen/types/ProcessDefinitionInstanceStatisticsQueryResult';
import type {ProcessDefinitionInstanceStatisticsResult} from './gen/types/ProcessDefinitionInstanceStatisticsResult';
import type {ProcessDefinitionInstanceVersionStatisticsQuery} from './gen/types/ProcessDefinitionInstanceVersionStatisticsQuery';
import type {ProcessDefinitionInstanceVersionStatisticsQueryResult} from './gen/types/ProcessDefinitionInstanceVersionStatisticsQueryResult';
import type {ProcessDefinitionInstanceVersionStatisticsResult} from './gen/types/ProcessDefinitionInstanceVersionStatisticsResult';
import type {ProcessDefinitionResult} from './gen/types/ProcessDefinitionResult';
import type {ProcessDefinitionSearchQuery} from './gen/types/ProcessDefinitionSearchQuery';
import type {ProcessDefinitionSearchQueryResult} from './gen/types/ProcessDefinitionSearchQueryResult';
import {
	processDefinitionSchema,
	processDefinitionStateSchema,
	processDefinitionStatisticSchema,
	type ProcessDefinition,
	type ProcessDefinitionState,
	type StatisticName,
	type ProcessDefinitionStatistic,
} from './processes';

const processDefinitionResponseSchema = processDefinitionResultSchema;

const getProcessDefinitionResponseBodySchema = processDefinitionResultSchema;
type GetProcessDefinitionResponseBody = ProcessDefinitionResult;

const getProcessStartFormResponseBodySchema = getStartProcessFormStatus200Schema;
type GetProcessStartFormResponseBody = GetStartProcessFormStatus200;

const getProcessDefinitionXmlResponseBodySchema = getProcessDefinitionXMLStatus200Schema;
type GetProcessDefinitionXmlResponseBody = GetProcessDefinitionXMLStatus200;

const getProcessDefinitionStatisticsRequestBodySchema = processDefinitionElementStatisticsQuerySchema;
type GetProcessDefinitionStatisticsRequestBody = ProcessDefinitionElementStatisticsQuery;

const getProcessDefinitionStatisticsResponseBodySchema = processDefinitionElementStatisticsQueryResultSchema;
type GetProcessDefinitionStatisticsResponseBody = ProcessDefinitionElementStatisticsQueryResult;

const queryProcessDefinitionsRequestBodySchema = processDefinitionSearchQuerySchema;
type QueryProcessDefinitionsRequestBody = ProcessDefinitionSearchQuery;

const queryProcessDefinitionsResponseBodySchema = processDefinitionSearchQueryResultSchema;
type QueryProcessDefinitionsResponseBody = ProcessDefinitionSearchQueryResult;

const processDefinitionInstanceStatisticsSchema = processDefinitionInstanceStatisticsResultSchema;
type ProcessDefinitionInstanceStatistics = ProcessDefinitionInstanceStatisticsResult;

const getProcessDefinitionInstanceStatisticsRequestBodySchema = processDefinitionInstanceStatisticsQuerySchema;
type GetProcessDefinitionInstanceStatisticsRequestBody = ProcessDefinitionInstanceStatisticsQuery;

const getProcessDefinitionInstanceStatisticsResponseBodySchema = processDefinitionInstanceStatisticsQueryResultSchema;
type GetProcessDefinitionInstanceStatisticsResponseBody = ProcessDefinitionInstanceStatisticsQueryResult;

const processDefinitionInstanceVersionStatisticsSchema = processDefinitionInstanceVersionStatisticsResultSchema;
type ProcessDefinitionInstanceVersionStatistics = ProcessDefinitionInstanceVersionStatisticsResult;

const getProcessDefinitionInstanceVersionStatisticsRequestBodySchema =
	processDefinitionInstanceVersionStatisticsQuerySchema;
type GetProcessDefinitionInstanceVersionStatisticsRequestBody = ProcessDefinitionInstanceVersionStatisticsQuery;

const getProcessDefinitionInstanceVersionStatisticsResponseBodySchema =
	processDefinitionInstanceVersionStatisticsQueryResultSchema;
type GetProcessDefinitionInstanceVersionStatisticsResponseBody = ProcessDefinitionInstanceVersionStatisticsQueryResult;

const getProcessDefinition = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}` as const,
} as const satisfies Endpoint<Pick<ProcessDefinition, 'processDefinitionKey'>>;

const getProcessDefinitionXml = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}/xml` as const,
} as const satisfies Endpoint<Pick<ProcessDefinition, 'processDefinitionKey'>>;

const getProcessStartForm = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}/form` as const,
} as const satisfies Endpoint<Pick<ProcessDefinition, 'processDefinitionKey'>>;

const getProcessDefinitionInstanceStatistics = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/statistics/process-instances` as const,
} as const satisfies Endpoint;

type GetProcessDefinitionStatisticsParams = Pick<ProcessDefinition, 'processDefinitionKey'> & {
	statisticName: StatisticName;
};

const getProcessDefinitionStatistics = {
	method: 'POST',
	getUrl: ({processDefinitionKey, statisticName = 'element-instances'}) =>
		`/${API_VERSION}/process-definitions/${processDefinitionKey}/statistics/${statisticName}` as const,
} as const satisfies Endpoint<GetProcessDefinitionStatisticsParams>;

const queryProcessDefinitions = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/search` as const,
} as const satisfies Endpoint;

const getProcessDefinitionInstanceVersionStatistics = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/statistics/process-instances-by-version` as const,
} as const satisfies Endpoint;

export {
	getProcessDefinition,
	getProcessDefinitionXml,
	getProcessStartForm,
	getProcessDefinitionResponseBodySchema,
	getProcessDefinitionXmlResponseBodySchema,
	getProcessStartFormResponseBodySchema,
	getProcessDefinitionStatistics,
	queryProcessDefinitions,
	processDefinitionSchema,
	processDefinitionStateSchema,
	processDefinitionResponseSchema,
	processDefinitionStatisticSchema,
	getProcessDefinitionStatisticsRequestBodySchema,
	getProcessDefinitionStatisticsResponseBodySchema,
	queryProcessDefinitionsRequestBodySchema,
	queryProcessDefinitionsResponseBodySchema,
	getProcessDefinitionInstanceStatistics,
	getProcessDefinitionInstanceStatisticsRequestBodySchema,
	getProcessDefinitionInstanceStatisticsResponseBodySchema,
	processDefinitionInstanceStatisticsSchema,
	getProcessDefinitionInstanceVersionStatistics,
	getProcessDefinitionInstanceVersionStatisticsRequestBodySchema,
	getProcessDefinitionInstanceVersionStatisticsResponseBodySchema,
	processDefinitionInstanceVersionStatisticsSchema,
};

export type {
	ProcessDefinition,
	ProcessDefinitionState,
	GetProcessDefinitionResponseBody,
	GetProcessDefinitionXmlResponseBody,
	GetProcessStartFormResponseBody,
	ProcessDefinitionStatistic,
	GetProcessDefinitionStatisticsRequestBody,
	GetProcessDefinitionStatisticsResponseBody,
	QueryProcessDefinitionsRequestBody,
	QueryProcessDefinitionsResponseBody,
	GetProcessDefinitionInstanceStatisticsRequestBody,
	GetProcessDefinitionInstanceStatisticsResponseBody,
	ProcessDefinitionInstanceStatistics,
	GetProcessDefinitionInstanceVersionStatisticsRequestBody,
	GetProcessDefinitionInstanceVersionStatisticsResponseBody,
	ProcessDefinitionInstanceVersionStatistics,
};
