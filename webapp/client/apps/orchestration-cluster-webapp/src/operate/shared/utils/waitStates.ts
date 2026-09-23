/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {WaitStateStatistic} from '@camunda/camunda-api-zod-schemas/8.10';
import {t} from 'i18next';

function hasProcessLevelWaitState(
	waitStateStatistics: WaitStateStatistic[] | undefined,
	processDefinitionId: string | undefined,
): boolean {
	if (waitStateStatistics === undefined || processDefinitionId === undefined) {
		return false;
	}

	return waitStateStatistics.some(({elementId}) => elementId === processDefinitionId);
}

function getWaitStateLabel(waitingCount: number): string | null {
	if (waitingCount <= 0) {
		return null;
	}
	return waitingCount === 1
		? t('operate.processInstance.header.waiting')
		: t('operate.processInstance.header.waitingCount', {count: waitingCount});
}

export {hasProcessLevelWaitState, getWaitStateLabel};
