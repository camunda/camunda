/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {InstanceHeader, type Column} from '#/operate/shared/InstanceHeader/InstanceHeader';
import {DrainingTag} from '#/operate/components/DrainingTag/DrainingTag';
import {drainingProcessDefinitionsQuery} from '#/operate/pages/Dashboard/InstancesByProcess/instancesByProcess.queries';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {hasCalledProcessInstances} from '#/operate/shared/utils/elements';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {getWaitStateLabel} from '#/operate/shared/utils/waitStates';
import {isWidthBelowBreakpoint, useMatchMedia} from '#/operate/shared/useMatchMedia';
import {processInstanceIncidentsCountQuery, useProcessInstanceWaitStateStatistics} from './processInstance.queries';
import {useProcessInstancePage} from './useProcessInstancePage';
import {ProcessInstanceHeaderSkeleton} from './ProcessInstanceHeaderSkeleton';
import {ProcessInstanceLink} from './styled';

type Props = {
	operations?: React.ReactNode;
};

const ProcessInstanceHeader: React.FC<Props> = ({operations}) => {
	const {t} = useTranslation();
	const {processInstance: instance} = useProcessInstancePage();
	const {
		processInstanceKey,
		processDefinitionId,
		processDefinitionKey,
		processDefinitionVersion,
		processDefinitionVersionTag,
		businessId,
		tenantId,
		startDate,
		endDate,
		state,
		hasIncident,
	} = instance;
	const processDefinitionName = getProcessDefinitionName(instance);
	const isReducedLayout = useMatchMedia(isWidthBelowBreakpoint('xlg'));
	const {data: waitStateStatistics} = useProcessInstanceWaitStateStatistics(instance);
	const waitingCount = waitStateStatistics?.find(({elementId}) => elementId === processDefinitionId)?.waitingCount ?? 0;
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const {data: currentUser} = useQuery(queries.getCurrentUser());
	const {data: draining} = useQuery(drainingProcessDefinitionsQuery());
	const {data: incidentsCount = 0} = useQuery({
		...processInstanceIncidentsCountQuery(processInstanceKey),
		enabled: hasIncident,
	});
	const tenant = currentUser?.tenants.find((item) => item.tenantId === tenantId)?.name ?? tenantId;
	const {data: diagram, isPending} = useDiagramXml(processDefinitionKey);
	const hasVersionTag = processDefinitionVersionTag !== null;
	const hasBusinessId = businessId !== null;
	const isDraining = draining?.byKey.has(processDefinitionKey) ?? false;
	const versionLinkTitle = isMultiTenancyEnabled
		? t('operate.processInstance.header.versionLinkTitleWithTenant', {
				name: processDefinitionName,
				version: processDefinitionVersion,
				tenant,
			})
		: t('operate.processInstance.header.versionLinkTitle', {
				name: processDefinitionName,
				version: processDefinitionVersion,
			});

	const columns: (Column & {name: string})[] = [
		{name: t('operate.processInstance.header.key'), title: processInstanceKey, content: processInstanceKey},
		{
			name: t('operate.processInstance.header.version'),
			hideOverflowingContent: false,
			content: (
				<ProcessInstanceLink
					to="/operate/processes"
					search={{
						process: processDefinitionId,
						version: processDefinitionVersion,
						active: true,
						incidents: true,
						suspended: true,
						completed: false,
						canceled: false,
						...(isMultiTenancyEnabled ? {tenantId} : {}),
					}}
					title={versionLinkTitle}
					aria-label={versionLinkTitle}
				>
					{processDefinitionVersion}
				</ProcessInstanceLink>
			),
		},
		{
			name: t('operate.processInstance.header.versionTag'),
			hidden: !hasVersionTag,
			title: t('operate.processInstance.header.versionTagDescription'),
			content: (
				<Tag size="sm" type="outline">
					{processDefinitionVersionTag}
				</Tag>
			),
		},
		{
			name: t('operate.processInstance.header.businessId'),
			hidden: !hasBusinessId,
			title: businessId ?? undefined,
			content: businessId,
		},
		{
			name: t('operate.processInstance.header.tenant'),
			hidden: !isMultiTenancyEnabled,
			title: tenant,
			content: tenant,
		},
		{
			name: t('operate.processInstance.header.startDate'),
			hidden: isReducedLayout,
			title: formatTimestamp(startDate),
			content: formatTimestamp(startDate),
			dataTestId: 'start-date',
		},
		{
			name: t('operate.processInstance.header.endDate'),
			hidden: endDate === null || isReducedLayout,
			title: formatTimestamp(endDate),
			content: formatTimestamp(endDate),
			dataTestId: 'end-date',
		},
		{
			name: t('operate.processInstance.header.calledInstances'),
			hidden: isReducedLayout,
			hideOverflowingContent: false,
			content: hasCalledProcessInstances(diagram?.businessObjects) ? (
				<ProcessInstanceLink
					to="/operate/processes"
					search={{
						parentProcessInstanceKey: processInstanceKey,
						active: true,
						incidents: true,
						suspended: true,
						completed: true,
						canceled: true,
					}}
					title={t('operate.processInstance.header.viewCalledInstancesTitle')}
					aria-label={t('operate.processInstance.header.viewCalledInstancesTitle')}
					onClick={() =>
						storeStateLocally('operate.panelStates', {
							...(getStateLocally('operate.panelStates') ?? {}),
							isProcessesFiltersCollapsed: false,
						})
					}
				>
					{t('operate.processInstance.header.viewCalledInstances')}
				</ProcessInstanceLink>
			) : (
				t('operate.processInstance.header.none')
			),
		},
	].filter(({hidden}) => !hidden);

	if (isPending) {
		return <ProcessInstanceHeaderSkeleton />;
	}

	return (
		<InstanceHeader
			instanceName={processDefinitionName}
			state={state === 'SUSPENDED' ? state : hasIncident ? 'INCIDENT' : state}
			incidentsCount={hasIncident ? incidentsCount : 0}
			nameSubtitle={getWaitStateLabel(waitingCount)}
			headerColumns={columns.map(({name}) => name)}
			bodyColumns={columns}
			additionalContent={
				<>
					{isDraining && (
						<DrainingTag
							label={t('operate.dashboard.draining')}
							description={t('operate.dashboard.drainingDescriptionVersion')}
							align="bottom-right"
						/>
					)}
					{operations}
				</>
			}
		/>
	);
};

export {ProcessInstanceHeader};
