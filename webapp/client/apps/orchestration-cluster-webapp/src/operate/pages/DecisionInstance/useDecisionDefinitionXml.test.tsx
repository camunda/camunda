/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense} from 'react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {cleanup, render} from 'vitest-browser-react';
import {afterEach, describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mockGetDecisionDefinitionXmlEndpoint} from '#/shared-test-modules/mock-handlers';
import {useDecisionDefinitionXml as useDecisionsPageXml} from '#/operate/pages/Decisions/useDecisionDefinitionXml';
import {useDecisionDefinitionXml} from './useDecisionDefinitionXml';

const XML = '<definitions />';

const InstanceXml: React.FC = () => {
	const {isError} = useDecisionDefinitionXml('1');

	return <div>{isError ? 'instance failed' : 'instance loading'}</div>;
};

const DecisionsXml: React.FC = () => {
	const {data} = useDecisionsPageXml('1');

	return <div>{data}</div>;
};

describe('useDecisionDefinitionXml', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should not let a failed instance XML request poison the Decisions page query', async ({worker}) => {
		// given
		const queryClient = new QueryClient();
		const wrapper = ({children}: {children: React.ReactNode}) => (
			<QueryClientProvider client={queryClient}>
				<Suspense fallback={null}>{children}</Suspense>
			</QueryClientProvider>
		);
		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		const instance = await render(<InstanceXml />, {wrapper});
		await expect.element(instance.getByText('instance failed')).toBeVisible();

		worker.use(mockGetDecisionDefinitionXmlEndpoint({successResponse: HttpResponse.text(XML)}));

		// when
		const decisions = await render(<DecisionsXml />, {wrapper});

		// then
		await expect.element(decisions.getByText(XML)).toBeVisible();
	});
});
