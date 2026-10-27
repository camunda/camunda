/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstanceInspection, WaitStateStatistic} from '@camunda/camunda-api-zod-schemas/8.11';
import {t} from 'i18next';
import {formatTimestamp} from './formatTimestamp';

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

function getEarliestTimerDueDate(waitStates: ElementInstanceInspection[]): number | null {
	const dueDates = waitStates.flatMap(({details}) =>
		details.waitStateType === 'TIMER' && details.dueDate !== null ? [details.dueDate] : [],
	);
	return dueDates.length > 0 ? Math.min(...dueDates) : null;
}

function getWaitStateStatusItems(waitStates: ElementInstanceInspection[]): Array<{key: string; text: string}> {
	const earliestTimerDueDate = getEarliestTimerDueDate(waitStates);
	const firstTimerIndex = waitStates.findIndex(({details}) => details.waitStateType === 'TIMER');

	return waitStates.flatMap(({elementInstanceKey, details}, index) => {
		switch (details.waitStateType) {
			case 'MESSAGE':
				return {
					key: `${elementInstanceKey}-MESSAGE-${details.messageName}`,
					text: t('operate.processInstance.details.waiting.message', {name: details.messageName}),
				};
			case 'TIMER': {
				if (index !== firstTimerIndex) {
					return [];
				}
				return earliestTimerDueDate !== null
					? {
							key: `TIMER-${earliestTimerDueDate}`,
							text: t('operate.processInstance.details.waiting.timerDue', {
								date: formatTimestamp(new Date(earliestTimerDueDate).toISOString()),
							}),
						}
					: {key: 'TIMER-waiting', text: t('operate.processInstance.details.waiting.timer')};
			}
			case 'SIGNAL':
				return {
					key: `${elementInstanceKey}-SIGNAL-${details.signalName}`,
					text: t('operate.processInstance.details.waiting.signal', {name: details.signalName}),
				};
			case 'CONDITION':
				return {
					key: `${elementInstanceKey}-CONDITION`,
					text: t('operate.processInstance.details.waiting.condition'),
				};
			case 'JOB': {
				const {jobType, jobKind} = details;
				if (jobKind === 'EXECUTION_LISTENER' || jobKind === 'TASK_LISTENER') {
					return {
						key: `${elementInstanceKey}-JOB-${jobKind}-${jobType}`,
						text: t(
							jobKind === 'EXECUTION_LISTENER'
								? 'operate.processInstance.details.waiting.executionListener'
								: 'operate.processInstance.details.waiting.taskListener',
							{name: jobType},
						),
					};
				}
				return {
					key: `${elementInstanceKey}-JOB-${jobType}`,
					text: t('operate.processInstance.details.waiting.job', {name: jobType}),
				};
			}
			case 'USER_TASK':
				return {
					key: `${elementInstanceKey}-USER_TASK`,
					text: t('operate.processInstance.details.waiting.userTask'),
				};
			default:
				return {key: `${elementInstanceKey}-WAITING`, text: t('operate.processInstance.details.waiting.generic')};
		}
	});
}

export {hasProcessLevelWaitState, getWaitStateLabel, getWaitStateStatusItems};
