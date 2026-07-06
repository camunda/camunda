/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ErrorComponentProps} from '@tanstack/react-router';
import {requestErrorSchema} from '#/shared/http/request';
import {TaskDetailsProcessError} from './TaskDetailsProcessError';

const TaskDetailsProcessRouteError: React.FC<ErrorComponentProps> = ({error, reset}) => {
	const result = requestErrorSchema.safeParse(error);

	if (result.success && result.data.response?.status === 403) {
		return <TaskDetailsProcessError variant="forbidden" />;
	}

	return <TaskDetailsProcessError variant="generic" onRetry={reset} />;
};

export {TaskDetailsProcessRouteError};
