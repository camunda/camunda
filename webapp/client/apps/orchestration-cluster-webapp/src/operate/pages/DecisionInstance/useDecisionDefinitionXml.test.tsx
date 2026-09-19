/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mockGetDecisionDefinitionXmlEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {useDecisionDefinitionXml} from './useDecisionDefinitionXml';

const DECISION_DEFINITION_KEY = '2251799813685253';

const Harness: React.FC = () => {
	const {isPending, isError} = useDecisionDefinitionXml(DECISION_DEFINITION_KEY);

	if (isPending) {
		return <div>loading</div>;
	}

	return <div>{isError ? 'error' : 'content'}</div>;
};

describe('useDecisionDefinitionXml', () => {
	it.for([{status: 403}, {status: 404}])(
		'should fail without retrying for a permanent $status response',
		async ({status}, {worker}) => {
			worker.use(
				mockGetDecisionDefinitionXmlEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status}), {status}),
				}),
			);
			const queryClient = new QueryClient();
			let xmlRequests = 0;
			const onRequest = ({request}: {request: Request}) => {
				if (
					request.method === 'GET' &&
					new URL(request.url).pathname.endsWith(`/decision-definitions/${DECISION_DEFINITION_KEY}/xml`)
				) {
					xmlRequests++;
				}
			};
			worker.events.on('request:start', onRequest);
			let screen: Awaited<ReturnType<typeof render>> | undefined;

			try {
				screen = await render(
					<QueryClientProvider client={queryClient}>
						<Harness />
					</QueryClientProvider>,
				);
				await expect.element(screen.getByText('error')).toBeVisible();
				await expect.poll(() => xmlRequests).toBe(1);
			} finally {
				await screen?.unmount();
				queryClient.clear();
				worker.events.removeListener('request:start', onRequest);
			}
		},
	);
});
