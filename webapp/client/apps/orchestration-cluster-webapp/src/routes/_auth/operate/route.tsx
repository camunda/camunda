/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {getClientConfig} from '#/modules/config/getClientConfig';
import {ComponentNotAvailableError, ForbiddenError} from '#/modules/errors/errors';
import {ForbiddenPage} from '#/pages/ForbiddenPage';

export const Route = createFileRoute('/_auth/operate')({
	beforeLoad: () => {
		if (!getClientConfig().components.active.includes('operate')) {
			throw new ComponentNotAvailableError('operate');
		}
	},
	errorComponent: ({error}) => {
		if (error instanceof ComponentNotAvailableError || error instanceof ForbiddenError) {
			return <ForbiddenPage />;
		}
		throw error;
	},
	component: OperateLayout,
	head: () => ({
		meta: [
			{
				title: 'Operate - Camunda',
			},
		],
	}),
});

function OperateLayout() {
	return <Outlet />;
}
