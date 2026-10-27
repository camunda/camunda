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

import {processDefinitionResultSchema} from './gen/zod/processDefinitionResultSchema';
import {processElementStatisticsResultSchema} from './gen/zod/processElementStatisticsResultSchema';
import {processInstanceResultSchema} from './gen/zod/processInstanceResultSchema';
import {processInstanceStateEnumSchema} from './gen/zod/processInstanceStateEnumSchema';
import type {ProcessDefinitionResult, ProcessDefinitionResultStateEnumKey} from './gen/types/ProcessDefinitionResult';
import type {ProcessElementStatisticsResult} from './gen/types/ProcessElementStatisticsResult';
import type {ProcessInstanceResult} from './gen/types/ProcessInstanceResult';
import type {ProcessInstanceStateEnumKey} from './gen/types/ProcessInstanceStateEnum';

const processInstanceStateSchema = processInstanceStateEnumSchema;
type ProcessInstanceState = ProcessInstanceStateEnumKey;
const processDefinitionStateSchema = processDefinitionResultSchema.shape.state;
type ProcessDefinitionState = ProcessDefinitionResultStateEnumKey;
type StatisticName = 'element-instances';

const processInstanceSchema = processInstanceResultSchema;
type ProcessInstance = ProcessInstanceResult;

const processDefinitionSchema = processDefinitionResultSchema;
type ProcessDefinition = ProcessDefinitionResult;

const processDefinitionStatisticSchema = processElementStatisticsResultSchema;
type ProcessDefinitionStatistic = ProcessElementStatisticsResult;

export {
	processInstanceStateSchema,
	processDefinitionStateSchema,
	processInstanceSchema,
	processDefinitionSchema,
	processDefinitionStatisticSchema,
};
export type {
	ProcessInstance,
	ProcessInstanceState,
	ProcessDefinitionState,
	StatisticName,
	ProcessDefinition,
	ProcessDefinitionStatistic,
};
