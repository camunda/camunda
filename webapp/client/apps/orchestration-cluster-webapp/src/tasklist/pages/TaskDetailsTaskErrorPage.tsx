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
import styles from './TaskDetailsTaskErrorPage.module.scss';
import {TruncatedVariableError} from '#/shared/errors';

const TaskDetailsTaskErrorPage: React.FC<ErrorComponentProps> = ({error, reset}) => {
	const {t} = useTranslation();
	const isTruncatedVariableError = error instanceof TruncatedVariableError;

	return (
		<div className={styles.container} data-testid="task-tab-content">
			<InlineNotification
				kind="error"
				hideCloseButton
				role="alert"
				title={t(
					isTruncatedVariableError
						? 'tasklist.taskDetailsTruncatedVariablesErrorTitle'
						: 'tasklist.taskDetailsFailedToFetchVariablesErrorTitle',
				)}
				subtitle={t(
					isTruncatedVariableError
						? 'tasklist.taskDetailsTruncatedVariablesErrorSubtitle'
						: 'tasklist.taskDetailsFailedToFetchVariablesErrorSubtitle',
				)}
			/>
			{isTruncatedVariableError ? null : (
				<Button kind="secondary" onClick={reset}>
					{t('tasklist.taskDetailsProcessRetryButtonLabel')}
				</Button>
			)}
		</div>
	);
};

export {TaskDetailsTaskErrorPage};
