/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';
import {mockGetElementInstanceEndpoint, mockQueryElementInstancesEndpoint} from '#/shared-test-modules/mock-handlers';

const INSTANCE = createProcessInstance({processInstanceKey: '2251799813900999', state: 'COMPLETED'});
const ELEMENTS = Array.from({length: 150}, (_, index) =>
	createElementInstance({
		processInstanceKey: INSTANCE.processInstanceKey,
		elementInstanceScopeKey: INSTANCE.processInstanceKey,
		elementId: `task-${index}`,
		elementInstanceKey: String(2251799813900000 + index),
		elementName: `Task ${index}`,
		state: 'COMPLETED',
	}),
);

function mockHistoryPage(items: typeof ELEMENTS) {
	return mockQueryElementInstancesEndpoint({
		successResponse: HttpResponse.json(createQueryElementInstancesResponse(items, ELEMENTS.length)),
	});
}

test.beforeEach(({network}) => {
	network.use(
		...processInstanceHeaderHandlers(INSTANCE),
		...processInstanceDiagramHandlers(),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(ELEMENTS[0])}),
	);
	network.use(mockHistoryPage(ELEMENTS.slice(0, 100)));
});

test.describe('Instance history paging', () => {
	test('should keep the rows the user was looking at in view when paging forward and back', async ({
		network,
		operatePreviewProcessInstancePage,
	}) => {
		await operatePreviewProcessInstancePage.goto(INSTANCE.processInstanceKey, '/variables');
		await expect(operatePreviewProcessInstancePage.historyItem(/Task 0$/)).toBeVisible();

		network.use(mockHistoryPage(ELEMENTS.slice(50, 150)));
		await operatePreviewProcessInstancePage.historyItem('Loading more elements').scrollIntoViewIfNeeded();

		await expect(operatePreviewProcessInstancePage.historyItem(/Task 149$/)).toBeAttached();
		await expect(operatePreviewProcessInstancePage.historyItem(/Task 99$/)).toBeInViewport();

		network.use(mockHistoryPage(ELEMENTS.slice(0, 100)));
		await operatePreviewProcessInstancePage.historyItem(/My Process$/).scrollIntoViewIfNeeded();

		await expect(operatePreviewProcessInstancePage.historyItem(/Task 0$/)).toBeAttached();
		await expect(operatePreviewProcessInstancePage.historyItem(/Task 50$/)).toBeInViewport();
	});

	test('should not let keyboard users land on the paging sentinel', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		await operatePreviewProcessInstancePage.goto(INSTANCE.processInstanceKey, '/variables');
		await expect(operatePreviewProcessInstancePage.historyItem(/Task 0$/)).toBeVisible();

		network.use(mockHistoryPage(ELEMENTS.slice(50, 150)));
		await operatePreviewProcessInstancePage.historyItem(/Task 0$/).click();
		await page.keyboard.press('End');

		await expect(operatePreviewProcessInstancePage.historyItem(/Task 99$/)).toBeFocused();
		await expect(operatePreviewProcessInstancePage.historyItem('Loading more elements')).not.toBeFocused();
	});
});
