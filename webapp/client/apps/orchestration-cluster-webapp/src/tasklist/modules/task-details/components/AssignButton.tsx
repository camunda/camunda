/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t as _t} from 'i18next';
import {useTranslation} from 'react-i18next';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {useTaskAssignment, type AssignmentStatus} from '#/tasklist/modules/task-details/useTaskAssignment';
import {AsyncActionButton} from './AsyncActionButton/AsyncActionButton';
import {useMemo} from 'react';

const getAssignmentToggleLabels = (): Record<Exclude<AssignmentStatus, 'off'>, string> => ({
	assigning: _t('tasklist.taskHeaderAssigning'),
	unassigning: _t('tasklist.taskHeaderUnassigning'),
	assignmentSuccessful: _t('tasklist.taskHeaderAssignmentSuccessful'),
	unassignmentSuccessful: _t('tasklist.taskHeaderUnassignmentSuccessful'),
});

type Props = {
	userTaskKey: string;
	assignee: string | null;
	taskState: UserTask['state'];
	currentUser: string;
};

const AssignButton: React.FC<Props> = ({userTaskKey, assignee, taskState, currentUser}) => {
	const isAssigned = typeof assignee === 'string' && taskState !== 'ASSIGNING';
	const {t} = useTranslation();
	const {status, isBusy, toggle} = useTaskAssignment({
		userTaskKey,
		currentUser,
		taskState,
		assignee,
	});

	function getAsyncActionButtonStatus() {
		if (isBusy || status !== 'off') {
			const ACTIVE_STATES: AssignmentStatus[] = ['assigning', 'unassigning'];

			return ACTIVE_STATES.includes(status) ? 'active' : 'finished';
		}

		return 'inactive';
	}

	const inlineLoadingProps = useMemo(
		() =>
			({
				description: status === 'off' ? undefined : getAssignmentToggleLabels()[status],
				'aria-live': ['assigning', 'unassigning'].includes(status) ? 'assertive' : 'polite',
			}) as const,
		[status],
	);
	const buttonProps = useMemo(
		() =>
			({
				kind: isAssigned ? 'ghost' : 'primary',
				size: 'sm',
				type: 'button',
				onClick: toggle,
				disabled: isBusy,
				autoFocus: true,
				id: 'main-content',
			}) as const,
		[isAssigned, toggle, isBusy],
	);

	return (
		<AsyncActionButton
			inlineLoadingProps={inlineLoadingProps}
			buttonProps={buttonProps}
			status={getAsyncActionButtonStatus()}
		>
			{isAssigned ? t('tasklist.taskDetailsUnassign') : t('tasklist.taskDetailsAssignToMe')}
		</AsyncActionButton>
	);
};

export {AssignButton};
