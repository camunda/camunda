/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Alert, Button} from '@camunda/design-system';
import type {ErrorComponentProps} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {TruncatedVariableError} from '#/shared/errors';

const TaskDetailsTaskErrorPage: React.FC<ErrorComponentProps> = ({error, reset}) => {
	const {t} = useTranslation();
	const isTruncatedVariableError = error instanceof TruncatedVariableError;

	return (
		<div className="flex h-full w-full flex-col items-center justify-center gap-4 p-4" data-testid="task-tab-content">
			<Alert
				className="w-full max-w-xl"
				variant="destructive"
				role="alert"
				title={t(
					isTruncatedVariableError
						? 'tasklist.taskDetailsTruncatedVariablesErrorTitle'
						: 'tasklist.taskDetailsFailedToFetchVariablesErrorTitle',
				)}
				description={t(
					isTruncatedVariableError
						? 'tasklist.taskDetailsTruncatedVariablesErrorSubtitle'
						: 'tasklist.taskDetailsFailedToFetchVariablesErrorSubtitle',
				)}
			/>
			{isTruncatedVariableError ? null : (
				<Button variant="secondary" onClick={reset}>
					{t('tasklist.taskDetailsProcessRetryButtonLabel')}
				</Button>
			)}
		</div>
	);
};

export {TaskDetailsTaskErrorPage};
