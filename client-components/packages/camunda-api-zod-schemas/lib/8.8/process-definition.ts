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
const processDefinitionStatisticSchema = processElementStatisticsResultSchema;
type ProcessDefinitionStatistic = z.infer<typeof processDefinitionStatisticSchema>;

const getProcessDefinition: Endpoint<{processDefinitionKey: string}> = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}`,
};

const getProcessDefinitionXml: Endpoint<{processDefinitionKey: string}> = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}/xml`,
};

const getProcessStartForm: Endpoint<{processDefinitionKey: string}> = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}/form`,
};

const getProcessDefinitionStatisticsRequestBodySchema = processDefinitionElementStatisticsQuerySchema;
type GetProcessDefinitionStatisticsRequestBody = z.infer<typeof getProcessDefinitionStatisticsRequestBodySchema>;

const getProcessDefinitionStatisticsResponseBodySchema = processDefinitionElementStatisticsQueryResultSchema;
type GetProcessDefinitionStatisticsResponseBody = z.infer<typeof getProcessDefinitionStatisticsResponseBodySchema>;

type GetProcessDefinitionStatisticsParams = {processDefinitionKey: string} & {
	statisticName: 'element-instances';
};

const getProcessDefinitionStatistics: Endpoint<GetProcessDefinitionStatisticsParams> = {
	method: 'POST',
	getUrl: ({processDefinitionKey, statisticName = 'element-instances'}) =>
		`/${API_VERSION}/process-definitions/${processDefinitionKey}/statistics/${statisticName}`,
};

const queryProcessDefinitionsRequestBodySchema = processDefinitionSearchQuerySchema;
type QueryProcessDefinitionsRequestBody = z.infer<typeof queryProcessDefinitionsRequestBodySchema>;

const queryProcessDefinitionsResponseBodySchema = processDefinitionSearchQueryResultSchema;
type QueryProcessDefinitionsResponseBody = z.infer<typeof queryProcessDefinitionsResponseBodySchema>;

const queryProcessDefinitions: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/search`,
};

export {
	getProcessDefinition,
	getProcessDefinitionXml,
	getProcessStartForm,
	getProcessDefinitionStatistics,
	queryProcessDefinitions,
	processDefinitionSchema,
	processDefinitionStatisticSchema,
	getProcessDefinitionStatisticsRequestBodySchema,
	getProcessDefinitionStatisticsResponseBodySchema,
	queryProcessDefinitionsRequestBodySchema,
	queryProcessDefinitionsResponseBodySchema,
};
export type {
	ProcessDefinition,
	ProcessDefinitionStatistic,
	GetProcessDefinitionStatisticsRequestBody,
	GetProcessDefinitionStatisticsResponseBody,
	QueryProcessDefinitionsRequestBody,
	QueryProcessDefinitionsResponseBody,
};
