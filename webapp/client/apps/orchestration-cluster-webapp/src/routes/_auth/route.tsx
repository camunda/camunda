/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet, redirect, useRouterState, type RegisteredRouter} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {SessionWatcher} from '#/shared/auth/components/SessionWatcher';
import {queries} from '#/shared/http/queries';
import {storeSessionState} from '#/shared/browser-storage/session-storage';
import {C3Provider} from '#/shared/c3/components/C3Provider';
import {fetchSaasToken} from '#/shared/c3/fetchSaasToken';
import {Header} from '#/shared/header/components/Header';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {tracking} from '#/shared/tracking';
import {NotFoundPage} from '#/shared/pages/NotFoundPage';

export const Route = createFileRoute('/_auth')({
	beforeLoad: async ({location, context: {queryClient}}) => {
		try {
			const [currentUser, systemConfig] = await Promise.all([
				queryClient.ensureQueryData(queries.getCurrentUser()),
				queryClient.ensureQueryData(queries.getSystemConfiguration()),
				queryClient.ensureQueryData(queries.getLicense()),
			]);

			tracking.identifyUser(currentUser);

			storeSessionState('clientConfig', systemConfig);
		} catch {
			queryClient.cancelQueries();
			queryClient.clear();
			throw redirect({
				to: '/login',
				search: location.href === '/' ? {} : {redirect: location.href},
			});
		}
	},
	loader: async () => {
		const {organizationId} = getBootConfig();

		if (organizationId === null) {
			return {initialSaasToken: null};
		}

		return {initialSaasToken: await fetchSaasToken()};
	},
	component: RouteComponent,
	notFoundComponent: () => (
		<main className="cds--content">
			<NotFoundPage />
		</main>
	),
});

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];

const componentIndexes = {
	tasklist: '/tasklist',
	operate: '/operate',
	admin: '/admin',
} as const satisfies Record<string, FileRouteTypes['to']>;

type CurrentApp = 'tasklist' | 'operate' | 'admin';

function resolveCurrentApp(pathname: string): CurrentApp | undefined {
	if (pathname.startsWith(componentIndexes['tasklist'])) {
		return 'tasklist';
	}
	if (pathname.startsWith(componentIndexes['admin'])) {
		return 'admin';
	}
	if (pathname.startsWith(componentIndexes['operate'])) {
		return 'operate';
	}

	return undefined;
}

function RouteComponent() {
	const {initialSaasToken} = Route.useLoaderData();
	const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
	const {data: license} = useSuspenseQuery(queries.getLicense());
	const pathname = useRouterState({select: ({location}) => location.pathname});
	const currentApp = resolveCurrentApp(pathname);

	return (
		<>
			<SessionWatcher />
			<C3Provider currentApp={currentApp} initialSaasToken={initialSaasToken}>
				<Header currentUser={currentUser} license={license} />
				<Outlet />
			</C3Provider>
		</>
	);
}
