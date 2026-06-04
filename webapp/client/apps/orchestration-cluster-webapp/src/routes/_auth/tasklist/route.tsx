/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {ComponentNotAvailableError, ForbiddenError} from '#/shared/errors/errors';
import {ForbiddenPage} from '#/shared/pages/ForbiddenPage';

export const Route = createFileRoute('/_auth/tasklist')({
	beforeLoad: () => {
		if (!getClientConfig().components.active.includes('tasklist')) {
			throw new ComponentNotAvailableError('tasklist');
		}
	},
	errorComponent: ({error}) => {
		if (error instanceof ComponentNotAvailableError || error instanceof ForbiddenError) {
			return <ForbiddenPage />;
		}
		throw error;
	},
	component: TasklistLayout,
	head: () => ({
		meta: [
			{
				title: 'Tasklist - Camunda',
			},
		],
	}),
});

function TasklistLayout() {
	return <Outlet />;
}
