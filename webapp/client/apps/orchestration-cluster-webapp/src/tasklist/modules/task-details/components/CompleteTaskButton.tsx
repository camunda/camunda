/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t as _t} from 'i18next';
import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import type {CompletionStatus} from '#/tasklist/modules/task-details/useTaskCompletion';
import {AsyncActionButton} from './AsyncActionButton/AsyncActionButton';

const getCompletionButtonDescription = (status: CompletionStatus): string | undefined => {
	if (status === 'active') {
		return _t('tasklist.taskDetailsCompletingTaskMessage');
	}

	if (status === 'error') {
		return _t('tasklist.taskDetailsCompletionFailedMessage');
	}

	if (status === 'finished') {
		return _t('tasklist.taskDetailsCompletedTaskMessage');
	}

	return undefined;
};

type Props = {
	status: CompletionStatus;
	isDisabled: boolean;
	isHidden: boolean;
	onClick: () => void;
};

const CompleteTaskButton: React.FC<Props> = ({status, isDisabled, isHidden, onClick}) => {
	const {t} = useTranslation();
	const inlineLoadingProps = useMemo(
		() =>
			({
				description: getCompletionButtonDescription(status),
				'aria-live': status === 'active' ? 'assertive' : 'polite',
			}) as const,
		[status],
	);
	const buttonProps = useMemo(
		() =>
			({
				size: 'md',
				type: 'button',
				disabled: status === 'active' || isDisabled,
				onClick,
				title: isDisabled ? t('tasklist.taskDetailsDisabledCompleteButtonTitle') : undefined,
			}) as const,
		[status, isDisabled, onClick, t],
	);

	return (
		<AsyncActionButton
			inlineLoadingProps={inlineLoadingProps}
			buttonProps={buttonProps}
			status={status}
			isHidden={isHidden}
		>
			{t('tasklist.taskDetailsCompleteTaskButtonLabel')}
		</AsyncActionButton>
	);
};

export {CompleteTaskButton};
