/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {getClientConfig} from '#/modules/config/getClientConfig';
import {ComponentNotAvailableError} from '#/modules/errors/errors';
import {Forbidden} from '#/pages/forbidden';

export const Route = createFileRoute('/_auth/admin')({
	beforeLoad: () => {
		if (!getClientConfig().components.active.includes('admin')) {
			throw new ComponentNotAvailableError('admin');
		}
	},
	errorComponent: ({error}) => {
		if (error instanceof ComponentNotAvailableError) {
			return <Forbidden />;
		}
		throw error;
	},
	component: AdminLayout,
	head: () => ({
		meta: [
			{
				title: 'Admin - Camunda',
			},
		],
	}),
});

function AdminLayout() {
	return <Outlet />;
}
