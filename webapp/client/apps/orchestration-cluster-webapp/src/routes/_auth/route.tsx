/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet, redirect} from '@tanstack/react-router';
import {queries} from '#/modules/queries';
import {SessionWatcher} from '../../modules/auth/components/SessionWatcher';

export const Route = createFileRoute('/_auth')({
	beforeLoad: async ({context, location}) => {
		try {
			await context.queryClient.ensureQueryData(queries.currentUser);
		} catch {
			throw redirect({to: '/login', search: {redirect: location.href}});
		}
	},
	component: RouteComponent,
});

function RouteComponent() {
	return (
		<>
			<SessionWatcher />
			<Outlet />
		</>
	);
}
