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
import {ForbiddenError} from '#/shared/errors';

type Props = {
	variant: 'forbidden' | 'generic';
	onRetry?: () => void;
};

const TaskDetailsProcessError: React.FC<Props> = ({variant, onRetry}) => {
	const {t} = useTranslation();
	const isForbidden = variant === 'forbidden';

	return (
		<div className="flex min-h-0 w-full flex-1 flex-col items-start gap-4 p-4" data-testid="process-tab-content">
			<Alert
				className="w-full"
				variant="destructive"
				role="alert"
				title={t(
					isForbidden ? 'tasklist.taskDetailsProcessForbiddenTitle' : 'tasklist.taskDetailsProcessLoadErrorTitle',
				)}
				description={t(isForbidden ? 'tasklist.taskActionForbidden' : 'tasklist.taskDetailsProcessLoadErrorDesc')}
			/>
			{!isForbidden && onRetry !== undefined ? (
				<Button variant="secondary" onClick={onRetry}>
					{t('tasklist.taskDetailsProcessRetryButtonLabel')}
				</Button>
			) : null}
		</div>
	);
};

const TaskDetailsProcessRouteError: React.FC<ErrorComponentProps> = ({error, reset}) => {
	if (error instanceof ForbiddenError) {
		return <TaskDetailsProcessError variant="forbidden" />;
	}

	return <TaskDetailsProcessError variant="generic" onRetry={reset} />;
};

export {TaskDetailsProcessRouteError};
