/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet, redirect} from '@tanstack/react-router';
import {useSessionHeartbeat} from '@camunda/session-heartbeat/react';
import {SessionWatcher} from '#/shared/auth/shadcn.components/SessionWatcher';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {endpoints} from '#/shared/http/endpoints';
import {getCsrfTokenFromStorage} from '#/shared/http/request';
import {queries} from '#/shared/http/queries';
import {reactQueryClient} from '#/shared/http/reactQueryClient';
import {storeSessionState} from '#/shared/browser-storage/session-storage';
import {Header} from '#/shared/header/shadcn.components/Header';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';
import {PageLayout} from '@camunda/design-system';

export const Route = createFileRoute('/_shadcn/_auth')({
	beforeLoad: async ({location, context: {queryClient}}) => {
		try {
			const [, systemConfig] = await Promise.all([
				queryClient.ensureQueryData(queries.getCurrentUser()),
				queryClient.ensureQueryData(queries.getSystemConfiguration()),
				queryClient.ensureQueryData(queries.getLicense()),
			]);

			storeSessionState('clientConfig', systemConfig);
		} catch {
			queryClient.cancelQueries();
			queryClient.clear();
			const isTasklistIndex = location.href === '/tasklist';

			throw redirect({
				to: '/tasklist/login',
				search: isTasklistIndex ? {} : {redirect: location.href},
			});
		}
	},
	notFoundComponent: () => (
		<PageLayout>
			<NotFoundPage />
		</PageLayout>
	),
	component: function RouteComponent() {
		useSessionHeartbeat({
			url: endpoints.sessionHeartbeatUrl(),
			csrfToken: getCsrfTokenFromStorage,
			onUnauthorized: () => {
				authenticationStore.disableSession();
				reactQueryClient.clear();
			},
		});

		return (
			<>
				<SessionWatcher />
				<Header>
					<Outlet />
				</Header>
			</>
		);
	},
});
