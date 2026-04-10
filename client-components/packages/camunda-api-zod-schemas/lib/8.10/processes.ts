/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * This file exists only to avoid circular dependencies. Do not export it directly.
 */

import {z} from 'zod';

const processInstanceStateSchema = z.enum(['ACTIVE', 'COMPLETED', 'TERMINATED']);
type ProcessInstanceState = z.infer<typeof processInstanceStateSchema>;
type StatisticName = 'element-instances';

const processInstanceSchema = z.object({
	processDefinitionId: z.string(),
	processDefinitionName: z.string(),
	processDefinitionVersion: z.number(),
	processDefinitionVersionTag: z.string().nullable(),
	startDate: z.string(),
	endDate: z.string().nullable(),
	state: processInstanceStateSchema,
	hasIncident: z.boolean(),
	tenantId: z.string(),
	processInstanceKey: z.string(),
	processDefinitionKey: z.string(),
	parentProcessInstanceKey: z.string().nullable(),
	parentElementInstanceKey: z.string().nullable(),
	rootProcessInstanceKey: z.string().nullable(),
	tags: z.array(z.string()),
});
type ProcessInstance = z.infer<typeof processInstanceSchema>;

const processDefinitionSchema = z.object({
	name: z.string().nullable(),
	resourceName: z.string().nullable(),
	version: z.number(),
	versionTag: z.string().nullable(),
	processDefinitionId: z.string(),
	tenantId: z.string(),
	processDefinitionKey: z.string(),
	hasStartForm: z.boolean(),
});
type ProcessDefinition = z.infer<typeof processDefinitionSchema>;

const processDefinitionStatisticSchema = z.object({
	elementId: z.string(),
	active: z.number(),
	canceled: z.number(),
	incidents: z.number(),
	completed: z.number(),
});
type ProcessDefinitionStatistic = z.infer<typeof processDefinitionStatisticSchema>;

export {processInstanceStateSchema, processInstanceSchema, processDefinitionSchema, processDefinitionStatisticSchema};
export type {ProcessInstance, ProcessInstanceState, StatisticName, ProcessDefinition, ProcessDefinitionStatistic};
