/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {Link, useMatchRoute, useNavigate} from '@tanstack/react-router';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Tag} from '@carbon/react';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {hasProcessLevelWaitState} from '#/operate/shared/utils/waitStates';
import {isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {selectionIncidentsCountQuery, useProcessInstanceWaitStateStatistics} from './processInstance.queries';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstancePage} from './useProcessInstancePage';
import {TabNav} from './processInstanceTabs.styled';

const TAB_PATHS = {
	history: '/operate/processes/$processInstanceId/history',
	incidents: '/operate/processes/$processInstanceId/incidents',
	details: '/operate/processes/$processInstanceId/details',
	variables: '/operate/processes/$processInstanceId/variables',
	'operations-log': '/operate/processes/$processInstanceId/operations-log',
} as const;

type TabKey = keyof typeof TAB_PATHS;

function useSelectionIncidentsCount(processInstance: ProcessInstance) {
	const {resolvedElementInstance, isFetchingElement, selectedElementId} = useProcessInstanceElementSelection();
	const elementInstanceKey = resolvedElementInstance?.elementInstanceKey;
	const isElementInstanceSelected =
		elementInstanceKey !== undefined && elementInstanceKey !== processInstance.processInstanceKey;
	const {data} = useQuery({
		...selectionIncidentsCountQuery({
			processInstanceKey: processInstance.processInstanceKey,
			...(isElementInstanceSelected ? {elementInstanceKey} : {elementId: selectedElementId ?? undefined}),
		}),
		enabled: processInstance.hasIncident && (isElementInstanceSelected || !isFetchingElement),
	});

	return data;
}

const ProcessInstanceTabs: React.FC<{isHistoryTabVisible: boolean}> = ({isHistoryTabVisible}) => {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	const navigate = useNavigate();
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {hasSelection, resolvedElementInstance} = useProcessInstanceElementSelection();
	const {data: waitStateStatistics} = useProcessInstanceWaitStateStatistics(processInstance);
	const incidentsCount = useSelectionIncidentsCount(processInstance);
	const isProcessLevelWaiting = hasProcessLevelWaitState(waitStateStatistics, processInstance.processDefinitionId);

	const tabs: {key: TabKey; label: string; visible: boolean; tag?: number}[] = [
		{key: 'history', label: t('operate.processInstance.history.title'), visible: isHistoryTabVisible},
		{
			key: 'incidents',
			label: t('operate.processInstance.history.incidents'),
			// A call activity or business rule task can have no incident of its own while the incident is below it.
			visible:
				processInstance.hasIncident &&
				(incidentsCount === undefined || incidentsCount > 0 || resolvedElementInstance?.hasIncident === true),
			tag: incidentsCount ?? 0,
		},
		{
			key: 'details',
			label: t('operate.processInstance.history.details'),
			visible: hasSelection || isProcessLevelWaiting,
		},
		{key: 'variables', label: t('operate.processInstance.history.variables'), visible: true},
		{key: 'operations-log', label: t('operate.processInstance.history.operationsLog'), visible: true},
	];

	const selectedTab = tabs.find(({key}) => matchRoute({to: TAB_PATHS[key]}));
	const isDeferringRedirect = !hasSelection && isInstanceRunning(processInstance) && waitStateStatistics === undefined;
	const redirectTarget =
		selectedTab !== undefined && selectedTab.key !== 'history' && !selectedTab.visible && !isDeferringRedirect
			? TAB_PATHS[hasSelection || isProcessLevelWaiting ? 'details' : 'variables']
			: null;

	useEffect(() => {
		if (redirectTarget !== null) {
			void navigate({to: redirectTarget, params: {processInstanceId}, search: true, replace: true});
		}
	}, [navigate, redirectTarget, processInstanceId]);

	return (
		<TabNav className="cds--tabs" aria-label={t('operate.processInstance.history.tabs')}>
			<div className="cds--tab--list">
				{tabs
					.filter(({visible}) => visible)
					.map(({key, label, tag}) => (
						<Link
							key={key}
							to={TAB_PATHS[key]}
							params={{processInstanceId}}
							search={true}
							className="cds--tabs__nav-item cds--tabs__nav-link"
							activeOptions={{includeSearch: false}}
							activeProps={{className: 'cds--tabs__nav-item--selected', 'aria-current': 'page'}}
						>
							<div className="cds--tabs__nav-item-label-wrapper">
								<span className="cds--tabs__nav-item-label">{label}</span>
								{tag !== undefined && (
									<Tag size="sm" type="red">
										{tag}
									</Tag>
								)}
							</div>
						</Link>
					))}
			</div>
		</TabNav>
	);
};

export {ProcessInstanceTabs};
