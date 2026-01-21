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
	processDefinitionSearchQuerySchema,
	processDefinitionSearchQueryResultSchema,
	processDefinitionElementStatisticsQuerySchema,
	processDefinitionElementStatisticsQueryResultSchema,
	processDefinitionResultSchema,
	processElementStatisticsResultSchema,
} from './gen';

const processDefinitionSchema = processDefinitionResultSchema;
type ProcessDefinition = z.infer<typeof processDefinitionSchema>;
const processDefinitionStateSchema = z.enum(['ACTIVE', 'DRAINING', 'DELETED']);
type ProcessDefinitionState = z.infer<typeof processDefinitionStateSchema>;
const processDefinitionStatisticSchema = processElementStatisticsResultSchema;
type ProcessDefinitionStatistic = z.infer<typeof processDefinitionStatisticSchema>;

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

const getProcessDefinitionStatisticsRequestBodySchema = processDefinitionElementStatisticsQuerySchema;
type GetProcessDefinitionStatisticsRequestBody = z.infer<typeof getProcessDefinitionStatisticsRequestBodySchema>;

const getProcessDefinitionStatisticsResponseBodySchema = processDefinitionElementStatisticsQueryResultSchema;
type GetProcessDefinitionStatisticsResponseBody = z.infer<typeof getProcessDefinitionStatisticsResponseBodySchema>;

type GetProcessDefinitionStatisticsParams = {processDefinitionKey: string} & {
	statisticName: 'element-instances';
};

const getProcessDefinitionStatistics = {
	method: 'POST',
	getUrl: ({processDefinitionKey, statisticName = 'element-instances'}) =>
		`/${API_VERSION}/process-definitions/${processDefinitionKey}/statistics/${statisticName}` as const,
} as const satisfies Endpoint<GetProcessDefinitionStatisticsParams>;

const queryProcessDefinitionsRequestBodySchema = processDefinitionSearchQuerySchema;
type QueryProcessDefinitionsRequestBody = z.infer<typeof queryProcessDefinitionsRequestBodySchema>;

const queryProcessDefinitionsResponseBodySchema = processDefinitionSearchQueryResultSchema;
type QueryProcessDefinitionsResponseBody = z.infer<typeof queryProcessDefinitionsResponseBodySchema>;

const queryProcessDefinitions = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/search` as const,
} as const satisfies Endpoint;

export {
	getProcessDefinition,
	getProcessDefinitionXml,
	getProcessStartForm,
	getProcessDefinitionStatistics,
	queryProcessDefinitions,
	processDefinitionSchema,
	processDefinitionStateSchema,
	processDefinitionStatisticSchema,
	getProcessDefinitionStatisticsRequestBodySchema,
	getProcessDefinitionStatisticsResponseBodySchema,
	queryProcessDefinitionsRequestBodySchema,
	queryProcessDefinitionsResponseBodySchema,
};
export type {
	ProcessDefinition,
	ProcessDefinitionState,
	ProcessDefinitionStatistic,
	GetProcessDefinitionStatisticsRequestBody,
	GetProcessDefinitionStatisticsResponseBody,
	QueryProcessDefinitionsRequestBody,
	QueryProcessDefinitionsResponseBody,
};
