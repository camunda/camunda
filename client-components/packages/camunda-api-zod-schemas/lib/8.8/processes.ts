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
import {
	processInstanceResultSchema,
	processInstanceStateEnumSchema,
	processDefinitionResultSchema,
	processElementStatisticsResultSchema,
} from './gen';

const processInstanceStateSchema = processInstanceStateEnumSchema;
type ProcessInstanceState = z.infer<typeof processInstanceStateSchema>;
type StatisticName = 'element-instances';

const processInstanceSchema = processInstanceResultSchema;
type ProcessInstance = z.infer<typeof processInstanceSchema>;

const processDefinitionSchema = processDefinitionResultSchema;
type ProcessDefinition = z.infer<typeof processDefinitionSchema>;

const processDefinitionStatisticSchema = processElementStatisticsResultSchema;
type ProcessDefinitionStatistic = z.infer<typeof processDefinitionStatisticSchema>;

export {processInstanceStateSchema, processInstanceSchema, processDefinitionSchema, processDefinitionStatisticSchema};
export type {ProcessInstance, ProcessInstanceState, StatisticName, ProcessDefinition, ProcessDefinitionStatistic};
