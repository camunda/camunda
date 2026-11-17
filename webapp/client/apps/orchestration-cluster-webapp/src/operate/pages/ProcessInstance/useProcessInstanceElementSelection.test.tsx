/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetElementInstanceEndpoint, mockQueryElementInstancesEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {processInstanceSearchSchema} from './processInstanceSearch';

const PROCESS_INSTANCE_ID = '2251799813685280';

afterEach(async () => {
	await cleanup();
});

function SelectionStatus() {
	const {isFetchingElementError} = useProcessInstanceElementSelection();

	return <p>{isFetchingElementError ? 'Selection failed' : 'Selection pending'}</p>;
}

function renderSelection(search: string) {
	function Selection() {
		return (
			<ProcessInstancePageProvider
				processInstanceId={PROCESS_INSTANCE_ID}
				processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
				search={processInstanceSearchSchema.parse(Object.fromEntries(new URLSearchParams(search)))}
			>
				<SelectionStatus />
			</ProcessInstancePageProvider>
		);
	}

	return renderWithRouter(Selection, {
		path: '/operate-preview/processes/$processInstanceId/variables',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables?${search}`,
	});
}

describe('useProcessInstanceElementSelection', () => {
	it('should report an error when the selected element instance cannot be fetched', async ({worker}) => {
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderSelection('elementInstanceKey=2251799813685290');

		await expect.element(screen.getByText('Selection failed')).toBeVisible();
	});

	it('should report an error when the instances of the selected element cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderSelection('elementId=task');

		await expect.element(screen.getByText('Selection failed')).toBeVisible();
	});
});
