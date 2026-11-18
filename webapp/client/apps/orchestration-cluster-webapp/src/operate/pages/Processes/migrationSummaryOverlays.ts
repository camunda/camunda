/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetProcessDefinitionStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';
import {MODIFICATIONS_BADGE} from '#/operate/shared/utils/badgePositions';
import {statisticsOverlaysParser} from './useDiagramStatisticsOverlays';

function getSourceSummaryOverlays(
	statistics: GetProcessDefinitionStatisticsResponseBody,
	businessObjects: BusinessObjects,
): OverlayData[] {
	return statisticsOverlaysParser(businessObjects)(statistics);
}

function getTargetSummaryOverlays(
	statistics: GetProcessDefinitionStatisticsResponseBody,
	mapping: Record<string, string>,
): OverlayData[] {
	const countByTargetId = Object.entries(mapping).reduce((counts, [sourceElementId, targetElementId]) => {
		const statistic = statistics.items.find(({elementId}) => elementId === sourceElementId);
		const count = (statistic?.active ?? 0) + (statistic?.incidents ?? 0);
		return counts.set(targetElementId, (counts.get(targetElementId) ?? 0) + count);
	}, new Map<string, number>());

	return [...countByTargetId].map(([elementId, newTokenCount]) => ({
		payload: {newTokenCount},
		type: 'batchModificationsBadge',
		elementId,
		position: MODIFICATIONS_BADGE,
	}));
}

export {getSourceSummaryOverlays, getTargetSummaryOverlays};
