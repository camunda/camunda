/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.11';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

type WaitStateKind = 'userTask' | 'message' | 'timer' | 'signal' | 'condition' | 'job' | 'listener' | 'adHoc' | 'stuck';

type WaitStateLabel = {
	id: string;
	kind: WaitStateKind;
	label: string;
	detail: string;
	extras: string[];
};

const KIND_PRIORITY: Record<WaitStateKind, number> = {
	stuck: 0,
	userTask: 1,
	message: 2,
	signal: 2,
	condition: 2,
	timer: 3,
	job: 4,
	listener: 4,
	adHoc: 4,
};

function getWaitStateKind({details}: ElementInstanceInspection): WaitStateKind {
	switch (details.waitStateType) {
		case 'USER_TASK':
			return 'userTask';
		case 'MESSAGE':
			return 'message';
		case 'TIMER':
			return 'timer';
		case 'SIGNAL':
			return 'signal';
		case 'CONDITION':
			return 'condition';
		case 'JOB':
			if (details.retries === 0) {
				return 'stuck';
			}

			if (details.jobKind === 'AD_HOC_SUB_PROCESS') {
				return 'adHoc';
			}

			if (details.jobKind === 'EXECUTION_LISTENER' || details.jobKind === 'TASK_LISTENER') {
				return 'listener';
			}

			return 'job';
	}
}

function getKindLabel(kind: WaitStateKind, waitState: ElementInstanceInspection): string {
	switch (kind) {
		case 'userTask':
			return t('tasklist.casesWaitUserTask');
		case 'message':
			return t('tasklist.casesWaitMessage');
		case 'timer':
			return t('tasklist.casesWaitTimer');
		case 'signal':
			return t('tasklist.casesWaitSignal');
		case 'condition':
			return t('tasklist.casesWaitCondition');
		case 'adHoc':
			return t('tasklist.casesWaitAdHoc');
		case 'listener':
			return t('tasklist.casesWaitListener', {
				eventType: waitState.details.waitStateType === 'JOB' ? waitState.details.listenerEventType : null,
			});
		case 'job':
		case 'stuck':
			return t('tasklist.casesWaitJob');
	}
}

function formatRelativeDate(isoDate: string): string | null {
	return formatISODateTime(isoDate)?.relative.text ?? null;
}

function getDetailAndExtras({
	elementId,
	details,
}: ElementInstanceInspection): Pick<WaitStateLabel, 'detail' | 'extras'> {
	switch (details.waitStateType) {
		case 'USER_TASK': {
			const dueDate = details.dueDate === null ? null : formatRelativeDate(details.dueDate);

			return {
				detail: elementId,
				extras: dueDate === null ? [] : [t('tasklist.casesWaitDue', {date: dueDate})],
			};
		}
		case 'MESSAGE':
			return {
				detail: details.messageName,
				extras: details.correlationKey ? [t('tasklist.casesWaitCorrelationKey', {key: details.correlationKey})] : [],
			};
		case 'TIMER': {
			const dueDate = details.dueDate === null ? null : formatRelativeDate(new Date(details.dueDate).toISOString());
			const extras = dueDate === null ? [] : [t('tasklist.casesWaitUntil', {date: dueDate})];

			// A plain (non-cycle) timer reports a single repetition.
			if (details.repetitions !== null && details.repetitions > 1) {
				extras.push(t('tasklist.casesWaitRepeats', {count: details.repetitions}));
			}

			return {detail: elementId, extras};
		}
		case 'SIGNAL':
			return {detail: details.signalName, extras: []};
		case 'CONDITION':
			return {detail: details.expression, extras: []};
		case 'JOB':
			return {
				detail: details.jobType,
				extras: details.retries === 0 ? [t('tasklist.casesWaitNoRetriesLeft')] : [],
			};
	}
}

function getWaitStateLabel(waitState: ElementInstanceInspection): WaitStateLabel {
	const kind = getWaitStateKind(waitState);
	const {detail, extras} = getDetailAndExtras(waitState);
	const isInCalledProcess =
		waitState.rootProcessInstanceKey !== null && waitState.processInstanceKey !== waitState.rootProcessInstanceKey;

	return {
		id: `${waitState.elementInstanceKey}-${waitState.details.waitStateType}`,
		kind,
		label: getKindLabel(kind, waitState),
		detail,
		extras: isInCalledProcess
			? [...extras, t('tasklist.casesWaitInProcess', {processId: waitState.bpmnProcessId})]
			: extras,
	};
}

type WaitStateGroup = {
	id: string;
	kind: WaitStateKind;
	label: string;
	waitStates: WaitStateLabel[];
};

function compareWaitStateLabels(first: WaitStateLabel, second: WaitStateLabel): number {
	return KIND_PRIORITY[first.kind] - KIND_PRIORITY[second.kind];
}

function groupWaitStateLabelsByKind(waitStates: WaitStateLabel[]): WaitStateGroup[] {
	const groups = new Map<string, WaitStateGroup>();

	for (const waitState of [...waitStates].sort(compareWaitStateLabels)) {
		const id = `${waitState.kind}-${waitState.label}`;
		const group = groups.get(id) ?? {id, kind: waitState.kind, label: waitState.label, waitStates: []};
		groups.set(id, {...group, waitStates: [...group.waitStates, waitState]});
	}

	return [...groups.values()];
}

export {compareWaitStateLabels, getWaitStateLabel, groupWaitStateLabelsByKind};
export type {WaitStateGroup, WaitStateKind, WaitStateLabel};
