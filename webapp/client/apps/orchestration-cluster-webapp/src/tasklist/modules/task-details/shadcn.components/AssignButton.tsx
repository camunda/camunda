/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {t as _t} from 'i18next';
import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useTaskAssignment, type AssignmentStatus} from '#/tasklist/modules/task-details/useTaskAssignment';
import {AsyncActionButton} from './AsyncActionButton';

const getAssignmentToggleDescription = (status: AssignmentStatus): string | undefined => {
	if (status === 'assigning') {
		return _t('tasklist.taskHeaderAssigning');
	}

	if (status === 'unassigning') {
		return _t('tasklist.taskHeaderUnassigning');
	}

	return undefined;
};

type Props = {
	userTaskKey: string;
	assignee: string | null;
	taskState: UserTask['state'];
	currentUser: string;
};

const AssignButton: React.FC<Props> = ({userTaskKey, assignee, taskState, currentUser}) => {
	const {t} = useTranslation();
	const isAssigned = typeof assignee === 'string' && taskState !== 'ASSIGNING';
	const {status, isBusy, toggle} = useTaskAssignment({
		userTaskKey,
		currentUser,
		taskState,
		assignee,
		isShadcn: true,
	});
	const loadingProps = useMemo(
		() =>
			({
				description: getAssignmentToggleDescription(status),
				ariaLive: isBusy ? 'assertive' : 'polite',
			}) as const,
		[status, isBusy],
	);
	const buttonProps = useMemo(
		() =>
			({
				variant: isAssigned ? 'secondary' : 'default',
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
		<AsyncActionButton {...loadingProps} buttonProps={buttonProps} status={isBusy ? 'active' : 'inactive'}>
			{isAssigned ? t('tasklist.taskDetailsUnassign') : t('tasklist.taskDetailsAssignToMe')}
		</AsyncActionButton>
	);
};

export {AssignButton};
