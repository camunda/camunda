/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, isRedirect, redirect} from '@tanstack/react-router';
import {z} from 'zod';
import {queries} from '#/shared/http/queries';
import {LegacyLoginPage} from '#/shared/pages/shadcn.components/LegacyLoginPage';
import {ThemeProvider} from '#/shared/theme/shadcn.components/ThemeProvider';
import tailwindCss from '#/shared/theme/tailwind.css?url';

// TODO(#64077): temporary DS reskin of the Carbon-hosted /login route, following the
// same "swap the visual now, cut the route over later" approach already used for the
// header's C3Navigation. Operate is this route's only remaining consumer (Admin and
// Tasklist already moved their logins under /_shadcn, and render the packaged
// `LoginScreen` component via LoginPage.tsx). Operate keeps the hand-composed
// LegacyLoginPage.tsx markup because it isn't migrated onto the design system yet,
// so it can't adopt packaged DS components. Once Operate's own route-level shadcn
// migration lands, delete this bridge and move the route under
// /_shadcn/_auth/operate/login, rendering LoginPage.tsx like Admin and Tasklist.
export const Route = createFileRoute('/_carbon/login')({
	validateSearch: z.object({
		redirect: z
			.string()
			.refine((val) => val.startsWith('/') && !val.startsWith('//'), 'Redirect must be an in-app relative path')
			.optional(),
	}),
	beforeLoad: async ({search, context: {queryClient}}) => {
		try {
			await queryClient.ensureQueryData(queries.getCurrentUser());
			throw redirect({href: search.redirect ?? '/operate', replace: true});
		} catch (e) {
			if (isRedirect(e)) {
				throw e;
			}
			// Not authenticated — show login form
		}
	},
	head: () => ({
		links: [{rel: 'stylesheet', href: tailwindCss}],
	}),
	component: () => (
		<ThemeProvider>
			<LegacyLoginPage title="Operate" />
		</ThemeProvider>
	),
});
