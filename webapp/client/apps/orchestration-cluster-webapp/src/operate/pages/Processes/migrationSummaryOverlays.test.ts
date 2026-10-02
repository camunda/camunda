/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	createGetProcessDefinitionStatisticsResponse,
	createProcessDefinitionStatistic,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {getTargetSummaryOverlays} from './migrationSummaryOverlays';

const STATISTICS = createGetProcessDefinitionStatisticsResponse([
	createProcessDefinitionStatistic({elementId: 'task-a', active: 2, incidents: 1, canceled: 5, completed: 7}),
	createProcessDefinitionStatistic({elementId: 'task-b', active: 4}),
	createProcessDefinitionStatistic({elementId: 'task-c', incidents: 3}),
]);

describe('getTargetSummaryOverlays', () => {
	it('should count only the running instances of every source element mapped to a target', () => {
		expect(
			getTargetSummaryOverlays(STATISTICS, {'task-a': 'target-1', 'task-b': 'target-1', 'task-c': 'target-2'}),
		).toEqual([
			{
				payload: {newTokenCount: 7},
				type: 'batchModificationsBadge',
				elementId: 'target-1',
				position: {top: -14, right: -7},
			},
			{
				payload: {newTokenCount: 3},
				type: 'batchModificationsBadge',
				elementId: 'target-2',
				position: {top: -14, right: -7},
			},
		]);
	});

	it('should count zero for a mapped source element without statistics', () => {
		expect(getTargetSummaryOverlays(STATISTICS, {'sequence-flow': 'target-flow'})).toEqual([
			{
				payload: {newTokenCount: 0},
				type: 'batchModificationsBadge',
				elementId: 'target-flow',
				position: {top: -14, right: -7},
			},
		]);
	});
});
