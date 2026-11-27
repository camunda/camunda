/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {afterEach, describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {Toaster, toast} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {createQueryDecisionDefinitionsResponse} from '#/shared-test-modules/api-mocks/decision-definitions';
import {mockQueryDecisionDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {decisionsRouteLoader} from './decisionsRouteLoader';

function runLoader(preload: boolean) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

	return decisionsRouteLoader({queryClient, decisionDefinitionId: 'missing-decision', preload}).catch(
		(error: unknown) => error,
	);
}

describe('decisionsRouteLoader', () => {
	afterEach(() => {
		notificationsStore.reset();
		toast.dismiss();
	});

	it('should show the not-found notification when navigating to an unknown decision', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
			}),
		);
		const screen = await render(
			<>
				<Toaster />
				<Notifications />
			</>,
		);

		await runLoader(false);

		await expect.element(screen.getByText("Couldn't find the decision", {exact: true})).toBeVisible();
	});
});
