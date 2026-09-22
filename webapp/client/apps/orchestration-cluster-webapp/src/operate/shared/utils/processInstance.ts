/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';

type ProcessDefinitionNameSource = Pick<ProcessDefinition, 'name' | 'processDefinitionId'>;
type ProcessInstanceNameSource = Pick<ProcessInstance, 'processDefinitionName' | 'processDefinitionId'>;

function getProcessDefinitionName(definition: ProcessDefinitionNameSource | ProcessInstanceNameSource) {
	return 'name' in definition
		? (definition.name ?? definition.processDefinitionId)
		: (definition.processDefinitionName ?? definition.processDefinitionId);
}

function isInstanceRunning(processInstance: Pick<ProcessInstance, 'state' | 'hasIncident'>): boolean {
	return processInstance.state === 'ACTIVE' || processInstance.hasIncident;
}

function shouldPollProcessInstance(processInstance: Pick<ProcessInstance, 'state' | 'hasIncident'>): boolean {
	return processInstance.state === 'SUSPENDED' || isInstanceRunning(processInstance);
}

export {getProcessDefinitionName, isInstanceRunning, shouldPollProcessInstance};
