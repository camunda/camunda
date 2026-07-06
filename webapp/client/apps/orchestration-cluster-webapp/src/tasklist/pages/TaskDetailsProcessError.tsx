/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, InlineNotification} from '@carbon/react';
import type {ErrorComponentProps} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {requestErrorSchema} from '#/shared/http/request';
import styles from './TaskDetailsProcessError.module.scss';

type Props = {
	variant: 'forbidden' | 'generic';
	onRetry?: () => void;
};

const TaskDetailsProcessError: React.FC<Props> = ({variant, onRetry}) => {
	const {t} = useTranslation();
	const isForbidden = variant === 'forbidden';

	return (
		<div className={styles.container} data-testid="process-tab-content">
			<InlineNotification
				kind="error"
				hideCloseButton
				role="alert"
				title={t(
					isForbidden ? 'tasklist.taskDetailsProcessForbiddenTitle' : 'tasklist.taskDetailsProcessLoadErrorTitle',
				)}
				subtitle={t(isForbidden ? 'tasklist.taskActionForbidden' : 'tasklist.taskDetailsProcessLoadErrorDesc')}
			/>
			{!isForbidden && onRetry !== undefined ? (
				<Button kind="secondary" onClick={onRetry}>
					{t('tasklist.taskDetailsProcessRetryButtonLabel')}
				</Button>
			) : null}
		</div>
	);
};

const TaskDetailsProcessRouteError: React.FC<ErrorComponentProps> = ({error, reset}) => {
	const result = requestErrorSchema.safeParse(error);

	if (result.success && result.data.response?.status === 403) {
		return <TaskDetailsProcessError variant="forbidden" />;
	}

	return <TaskDetailsProcessError variant="generic" onRetry={reset} />;
};

export {TaskDetailsProcessRouteError};
