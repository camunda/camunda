/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {hasProcessLevelWaitState} from '#/operate/shared/utils/waitStates';
import {
	isProcessInstanceWaitStateStatisticsEnabled,
	useProcessInstanceWaitStateStatistics,
} from '../processInstance.queries';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {useSelectionIncidentsCount} from '../useSelectionIncidentsCount';
import {getHiddenTabRedirect, getVisibleProcessInstanceTabs, type ProcessInstanceTab} from './processInstanceTabs';

function useVisibleProcessInstanceTabs(activeTab: ProcessInstanceTab) {
	const navigate = useNavigate();
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {hasSelection, resolvedElementInstance} = useProcessInstanceElementSelection();
	const {data: waitStateStatistics} = useProcessInstanceWaitStateStatistics(processInstance);
	const incidentsCount = useSelectionIncidentsCount(processInstance);
	const isProcessLevelWaiting = hasProcessLevelWaitState(waitStateStatistics, processInstance.processDefinitionId);
	const isWaitStateLoading =
		isProcessInstanceWaitStateStatisticsEnabled(processInstance) && waitStateStatistics === undefined;
	const hasIncidentsTab =
		processInstance.hasIncident &&
		(incidentsCount === undefined || incidentsCount > 0 || resolvedElementInstance?.hasIncident === true);
	const visibleTabs = getVisibleProcessInstanceTabs({hasIncidentsTab, hasSelection, isProcessLevelWaiting});
	const redirectTarget = getHiddenTabRedirect({
		activeTab,
		visibleTabs,
		hasSelection,
		isProcessLevelWaiting,
		isWaitStateLoading,
	});

	useEffect(() => {
		if (redirectTarget !== null) {
			void navigate({
				to:
					redirectTarget === 'details'
						? '/operate-preview/processes/$processInstanceId/details'
						: '/operate-preview/processes/$processInstanceId/variables',
				params: {processInstanceId},
				search: true,
				hash: true,
				replace: true,
			});
		}
	}, [redirectTarget, navigate, processInstanceId]);

	return {visibleTabs, incidentsCount, isActiveTabHidden: !visibleTabs.includes(activeTab)};
}

export {useVisibleProcessInstanceTabs};
