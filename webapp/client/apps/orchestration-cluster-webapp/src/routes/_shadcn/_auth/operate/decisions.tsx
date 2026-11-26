/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {operateRouteGuard} from '#/operate/shared/OperateRouteGuard/operateRouteGuard';

export const Route = createFileRoute('/_shadcn/_auth/operate/decisions')({
	...operateRouteGuard,
	component: () => <Outlet />,
	head: () => ({
		meta: [
			{
				title: 'Operate - Camunda',
			},
		],
	}),
});
