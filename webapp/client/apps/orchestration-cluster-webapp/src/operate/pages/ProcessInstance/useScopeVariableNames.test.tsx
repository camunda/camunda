/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockQueryVariablesEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {useModificationController} from './useProcessInstanceModification';
import {useScopeVariableNames} from './useScopeVariableNames';
import {variablesQuery} from './variables.queries';

const PROCESS_INSTANCE_ID = '2251799813685280';

function VariableNames() {
	const {names, isComplete} = useScopeVariableNames(PROCESS_INSTANCE_ID);

	return <output>{`${isComplete ? 'Complete' : 'Incomplete'}: ${names.length} names`}</output>;
}

function VariableNamesPage() {
	const [isLookupVisible, setIsLookupVisible] = useState(false);
	const modification = useModificationController(PROCESS_INSTANCE_ID);

	return (
		<ProcessInstancePageProvider
			processInstanceId={PROCESS_INSTANCE_ID}
			processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
			search={{}}
			modification={modification}
		>
			<button type="button" onClick={() => setIsLookupVisible(true)}>
				Load names
			</button>
			{isLookupVisible && <VariableNames />}
		</ProcessInstancePageProvider>
	);
}

describe('useScopeVariableNames', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should stop failed pagination while mounted and complete after an explicit refetch', async ({worker}) => {
		let nextPageRequests = 0;
		const nextPageSchema = z.object({page: z.object({after: z.literal('cursor-1')})}).transform((payload) => {
			nextPageRequests += 1;
			return payload;
		});
		const firstPage = HttpResponse.json(
			createQueryVariablesByUserTaskResponse({
				items: Array.from({length: 50}, (_, index) =>
					createVariable({name: `variable-${index}`, variableKey: `${index}`}),
				),
				page: {endCursor: 'cursor-1', totalItems: 51},
			}),
		);
		worker.use(
			mockQueryVariablesEndpoint({
				schema: nextPageSchema,
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
				failureResponse: firstPage,
			}),
		);
		const screen = await renderWithRouter(VariableNamesPage, {
			path: '/operate/processes/$processInstanceId/variables',
			initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/variables`,
		});
		const {queryKey} = variablesQuery({processInstanceKey: PROCESS_INSTANCE_ID, scopeKey: PROCESS_INSTANCE_ID});
		screen.queryClient.setQueryDefaults(queryKey, {retry: 2, retryDelay: 20});

		await userEvent.click(screen.getByRole('button', {name: 'Load names'}));

		await expect.poll(() => screen.queryClient.getQueryState(queryKey)?.status).toBe('error');
		await expect.element(screen.getByRole('status')).toHaveTextContent('Incomplete: 50 names');
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
		expect(nextPageRequests).toBe(3);

		worker.use(
			mockQueryVariablesEndpoint({
				schema: nextPageSchema,
				successResponse: HttpResponse.json(
					createQueryVariablesByUserTaskResponse({
						items: [createVariable({name: 'last-variable'})],
						page: {totalItems: 51},
					}),
				),
				failureResponse: firstPage,
			}),
		);

		await screen.queryClient.refetchQueries({queryKey, exact: true});

		await expect.element(screen.getByRole('status')).toHaveTextContent('Complete: 51 names');
		expect(nextPageRequests).toBe(4);
	});
});
