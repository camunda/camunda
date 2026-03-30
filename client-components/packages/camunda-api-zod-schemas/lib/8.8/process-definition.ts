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
	API_VERSION,
	advancedStringFilterSchema,
	getCollectionResponseBodySchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
	basicStringFilterSchema,
	getOrFilterSchema,
	advancedIntegerFilterSchema,
} from '../common';
import {
	processDefinitionSchema,
	processDefinitionStatisticSchema,
	processInstanceStateSchema,
	type ProcessDefinition,
	type StatisticName,
	type ProcessDefinitionStatistic,
} from './processes';

const getProcessDefinition: Endpoint<Pick<ProcessDefinition, 'processDefinitionKey'>> = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}`,
};

const getProcessDefinitionXml: Endpoint<Pick<ProcessDefinition, 'processDefinitionKey'>> = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}/xml`,
};

const getProcessStartForm: Endpoint<Pick<ProcessDefinition, 'processDefinitionKey'>> = {
	method: 'GET',
	getUrl: ({processDefinitionKey}) => `/${API_VERSION}/process-definitions/${processDefinitionKey}/form`,
};

const getProcessDefinitionInstanceStatistics: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/statistics/process-instances`,
};

const advancedProcessInstanceStateFilterSchema = z
	.object({
		$eq: processInstanceStateSchema,
		$neq: processInstanceStateSchema,
		$exists: z.boolean(),
		$in: z.array(processInstanceStateSchema),
		$like: z.string(),
	})
	.partial();

const processDefinitionStatisticsVariableFilterSchema = z.object({
	name: z.string(),
	value: advancedStringFilterSchema,
});

const processDefinitionStatisticsFilterFieldsSchema = z.object({
	startDate: advancedDateTimeFilterSchema,
	endDate: advancedDateTimeFilterSchema,
	state: advancedProcessInstanceStateFilterSchema,
	hasIncident: z.boolean(),
	tenantId: advancedStringFilterSchema,
	variables: z.array(processDefinitionStatisticsVariableFilterSchema),
	processInstanceKey: basicStringFilterSchema,
	parentProcessInstanceKey: basicStringFilterSchema,
	parentElementInstanceKey: basicStringFilterSchema,
	batchOperationId: advancedStringFilterSchema,
	errorMessage: advancedStringFilterSchema,
	hasRetriesLeft: z.boolean(),
	elementInstanceState: advancedProcessInstanceStateFilterSchema,
	elementId: advancedStringFilterSchema,
	hasElementInstanceIncident: z.boolean(),
	incidentErrorHashCode: advancedIntegerFilterSchema,
});

const getProcessDefinitionStatisticsRequestBodySchema = z
	.object({
		filter: getOrFilterSchema(processDefinitionStatisticsFilterFieldsSchema.partial()),
	})
	.partial();
type GetProcessDefinitionStatisticsRequestBody = z.infer<typeof getProcessDefinitionStatisticsRequestBodySchema>;

const getProcessDefinitionStatisticsResponseBodySchema = getCollectionResponseBodySchema(
	processDefinitionStatisticSchema,
);
type GetProcessDefinitionStatisticsResponseBody = z.infer<typeof getProcessDefinitionStatisticsResponseBodySchema>;

type GetProcessDefinitionStatisticsParams = Pick<ProcessDefinition, 'processDefinitionKey'> & {
	statisticName: StatisticName;
};

const getProcessDefinitionStatistics: Endpoint<GetProcessDefinitionStatisticsParams> = {
	method: 'POST',
	getUrl: ({processDefinitionKey, statisticName = 'element-instances'}) =>
		`/${API_VERSION}/process-definitions/${processDefinitionKey}/statistics/${statisticName}`,
};

const queryProcessDefinitionsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'processDefinitionKey',
		'name',
		'resourceName',
		'version',
		'versionTag',
		'processDefinitionId',
		'tenantId',
	] as const,
	filter: processDefinitionSchema
		.omit({
			processDefinitionId: true,
			name: true,
		})
		.extend({
			isLatestVersion: z.boolean(),
			processDefinitionId: advancedStringFilterSchema,
			name: advancedStringFilterSchema,
		})
		.partial(),
});
type QueryProcessDefinitionsRequestBody = z.infer<typeof queryProcessDefinitionsRequestBodySchema>;

const queryProcessDefinitionsResponseBodySchema = getQueryResponseBodySchema(processDefinitionSchema);
type QueryProcessDefinitionsResponseBody = z.infer<typeof queryProcessDefinitionsResponseBodySchema>;

const queryProcessDefinitions: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/search`,
};

const processDefinitionInstanceStatisticsSchema = z.object({
	processDefinitionId: z.string(),
	latestProcessDefinitionName: z.string(),
	hasMultipleVersions: z.boolean(),
	activeInstancesWithoutIncidentCount: z.number(),
	activeInstancesWithIncidentCount: z.number(),
	tenantId: z.string().optional(),
});
type ProcessDefinitionInstanceStatistics = z.infer<typeof processDefinitionInstanceStatisticsSchema>;

const getProcessDefinitionInstanceStatisticsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'processDefinitionId',
		'activeInstancesWithIncidentCount',
		'activeInstancesWithoutIncidentCount',
	] as const,
	filter: getOrFilterSchema(processDefinitionStatisticsFilterFieldsSchema.partial()),
});
type GetProcessDefinitionInstanceStatisticsRequestBody = z.infer<
	typeof getProcessDefinitionInstanceStatisticsRequestBodySchema
>;

const getProcessDefinitionInstanceStatisticsResponseBodySchema = getQueryResponseBodySchema(
	processDefinitionInstanceStatisticsSchema,
);
type GetProcessDefinitionInstanceStatisticsResponseBody = z.infer<
	typeof getProcessDefinitionInstanceStatisticsResponseBodySchema
>;

const processDefinitionInstanceVersionStatisticsSchema = z.object({
	processDefinitionId: z.string(),
	processDefinitionKey: z.string(),
	processDefinitionName: z.string(),
	processDefinitionVersion: z.number(),
	activeInstancesWithIncidentCount: z.number(),
	activeInstancesWithoutIncidentCount: z.number(),
	tenantId: z.string().optional(),
});
type ProcessDefinitionInstanceVersionStatistics = z.infer<typeof processDefinitionInstanceVersionStatisticsSchema>;

const processDefinitionVersionStatisticsFilterFieldsSchema = z.object({
	processDefinitionId: z.string(),
	tenantId: z.string().optional(),
});

const getProcessDefinitionInstanceVersionStatisticsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'processDefinitionId',
		'processDefinitionKey',
		'processDefinitionName',
		'processDefinitionVersion',
		'activeInstancesWithIncidentCount',
		'activeInstancesWithoutIncidentCount',
	] as const,
	filter: processDefinitionVersionStatisticsFilterFieldsSchema,
});

type GetProcessDefinitionInstanceVersionStatisticsRequestBody = z.infer<
	typeof getProcessDefinitionInstanceVersionStatisticsRequestBodySchema
>;

const getProcessDefinitionInstanceVersionStatisticsResponseBodySchema = getQueryResponseBodySchema(
	processDefinitionInstanceVersionStatisticsSchema,
);
type GetProcessDefinitionInstanceVersionStatisticsResponseBody = z.infer<
	typeof getProcessDefinitionInstanceVersionStatisticsResponseBodySchema
>;

const getProcessDefinitionInstanceVersionStatistics: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/process-definitions/statistics/process-instances-by-version`,
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
