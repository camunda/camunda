/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from '@tanstack/react-router';
import {useEffect, useRef} from 'react';
import {useQuery} from '@tanstack/react-query';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import type {ProcessInstanceSearch, ProcessInstanceSelection} from './processInstanceSearch';
import {ProcessInstanceContext} from './useProcessInstancePage';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {getProcessLevelWaitState} from '#/operate/shared/utils/instance';
import {waitStateStatisticsQuery} from './processInstance.queries';

function ProcessInstanceProvider({
	processInstance,
	search,
	children,
}: {
	processInstance: ProcessInstance;
	search: ProcessInstanceSearch;
	children: React.ReactNode;
}) {
	const navigate = useNavigate();
	const {data: diagram} = useDiagramXml(processInstance.processDefinitionKey);
	const {tab, ...selection} = search;
	const processInstanceId = processInstance.processInstanceKey;
	const {data: waitStates, isLoading} = useQuery(waitStateStatisticsQuery(processInstance));
	const processWaitState = getProcessLevelWaitState(waitStates, processInstance.processDefinitionId);
	const hasSelection = !!(selection.elementId || selection.elementInstanceKey);
	const defaultTab = processInstance.hasIncident
		? 'incidents'
		: hasSelection || processWaitState
			? 'details'
			: 'variables';
	const activeTab = tab ?? (isLoading ? undefined : defaultTab);
	useEffect(() => {
		if (tab === 'details' && !hasSelection && !processWaitState && !isLoading) {
			void navigate({
				to: '/operate/processes/$processInstanceId',
				params: {processInstanceId},
				search: (current) => ({...current, tab: 'variables'}),
				replace: true,
			});
		}
	}, [tab, hasSelection, processWaitState, isLoading, navigate, processInstanceId]);
	const previousElementId = useRef(selection.elementId);
	useEffect(() => {
		const elementId = selection.elementId;
		if (elementId && !diagram) {
			previousElementId.current ??= elementId;
			return;
		}
		const isSwitchingToCallActivity =
			previousElementId.current !== undefined &&
			elementId !== undefined &&
			previousElementId.current !== elementId &&
			diagram?.businessObjects[elementId]?.$type === 'bpmn:CallActivity';
		previousElementId.current = elementId;
		if (isSwitchingToCallActivity) {
			void navigate({
				to: '/operate/processes/$processInstanceId',
				params: {processInstanceId},
				search: (current) => ({...current, tab: processInstance.hasIncident ? 'incidents' : 'details'}),
				replace: true,
			});
		}
	}, [diagram, selection.elementId, navigate, processInstanceId, processInstance.hasIncident]);
	const select = (next: ProcessInstanceSelection) => {
		const isFirstSelection =
			!(selection.elementId || selection.elementInstanceKey) && !!(next.elementId || next.elementInstanceKey);
		void navigate({
			to: '/operate/processes/$processInstanceId',
			params: {processInstanceId},
			search: {
				tab: isFirstSelection ? (processInstance.hasIncident ? 'incidents' : 'details') : tab,
				...next,
			},
			replace: true,
		});
	};
	return (
		<ProcessInstanceContext
			value={{
				processInstanceId,
				processDefinitionKey: processInstance.processDefinitionKey,
				processInstance,
				search,
				selection,
				activeTab,
				waitingCount: processWaitState?.waitingCount ?? 0,
				selectElement: select,
				selectElementInstance: select,
				clearSelection: () => select({}),
				setActiveTab: (tab) => {
					void navigate({
						to: '/operate/processes/$processInstanceId',
						params: {processInstanceId},
						search: {...search, tab},
					});
				},
			}}
		>
			{children}
		</ProcessInstanceContext>
	);
}

export {ProcessInstanceProvider};
