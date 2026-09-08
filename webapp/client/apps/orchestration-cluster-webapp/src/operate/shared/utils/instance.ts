/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition, ProcessInstance, WaitStateStatistic} from '@camunda/camunda-api-zod-schemas/8.10';
import {t} from 'i18next';

function getProcessDefinitionName(
	definition:
		| Pick<ProcessDefinition, 'name' | 'processDefinitionId'>
		| Pick<ProcessInstance, 'processDefinitionName' | 'processDefinitionId'>,
) {
	const name = 'name' in definition ? definition.name : definition.processDefinitionName;
	return name ?? definition.processDefinitionId;
}

const isInstanceRunning = (instance: ProcessInstance) => instance.state === 'ACTIVE' || instance.hasIncident;

function getProcessLevelWaitState(statistics: WaitStateStatistic[] | undefined, processDefinitionId: string) {
	return statistics?.find(({elementId}) => elementId === processDefinitionId);
}

function getWaitStateLabel(count: number) {
	return count > 0 ? t('operate.shared.instanceHeader.waiting', {count}) : undefined;
}

export {getProcessDefinitionName, isInstanceRunning, getProcessLevelWaitState, getWaitStateLabel};
