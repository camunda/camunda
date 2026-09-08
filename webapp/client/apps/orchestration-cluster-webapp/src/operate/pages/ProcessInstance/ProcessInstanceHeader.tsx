/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import {useQuery} from '@tanstack/react-query';
import {useMediaQuery} from '@uidotdev/usehooks';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {InstanceHeader, type Column} from '#/operate/shared/InstanceHeader/InstanceHeader';
import {ProcessInstanceHeaderSkeleton} from './ProcessInstanceHeaderSkeleton';
import {formatEvaluationDate} from '#/operate/shared/utils/formatEvaluationDate';
import {DrainingTag} from '#/operate/components/DrainingTag/DrainingTag';
import {drainingProcessDefinitionsQuery} from '#/operate/pages/Dashboard/InstancesByProcess/instancesByProcess.queries';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {processInstanceIncidentsCountQuery} from './processInstance.queries';
import {useProcessInstancePage} from './useProcessInstancePage';
import {ProcessInstanceLink} from './styled';
import {getProcessDefinitionName, getWaitStateLabel} from '#/operate/shared/utils/instance';
import {hasCalledProcessInstances} from '#/operate/shared/utils/elements';

function ProcessInstanceHeader({operations}: {operations?: React.ReactNode}) {
	const {t} = useTranslation();
	const {processInstance: instance, waitingCount} = useProcessInstancePage();
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
	const name = getProcessDefinitionName(instance);
	const isReduced = useMediaQuery('(max-width: 1311px)');
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const {data: user} = useQuery(queries.getCurrentUser());
	const tenant = user?.tenants.find((item) => item.tenantId === tenantId)?.name ?? tenantId;
	const {data: draining} = useQuery(drainingProcessDefinitionsQuery());
	const {data: diagram, isPending} = useDiagramXml(processDefinitionKey);
	const {data: incidentsCount} = useQuery({
		...processInstanceIncidentsCountQuery(processInstanceKey),
		enabled: hasIncident,
	});
	const versionTitle =
		t('operate.processInstance.header.versionLink', {name, version: processDefinitionVersion}) +
		(isMultiTenancyEnabled ? ` - ${tenant}` : '');
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
						completed: false,
						canceled: false,
						...(isMultiTenancyEnabled ? {tenantId} : {}),
					}}
					title={versionTitle}
					aria-label={versionTitle}
				>
					{processDefinitionVersion}
				</ProcessInstanceLink>
			),
		},
		{
			name: t('operate.processInstance.header.versionTag'),
			hidden: processDefinitionVersionTag == null,
			title: t('operate.processInstance.header.versionTagDescription'),
			content: (
				<Tag size="sm" type="outline">
					{processDefinitionVersionTag}
				</Tag>
			),
		},
		{
			name: t('operate.processInstance.header.businessId'),
			hidden: businessId == null,
			title: businessId ?? undefined,
			content: businessId,
		},
		{name: t('operate.processInstance.header.tenant'), hidden: !isMultiTenancyEnabled, title: tenant, content: tenant},
		{
			name: t('operate.processInstance.header.startDate'),
			hidden: isReduced,
			title: formatEvaluationDate(startDate),
			content: formatEvaluationDate(startDate),
		},
		{
			name: t('operate.processInstance.header.endDate'),
			hidden: endDate === null || isReduced,
			title: formatEvaluationDate(endDate),
			content: formatEvaluationDate(endDate),
		},
		{
			name: t('operate.processInstance.header.calledInstances'),
			hidden: isReduced,
			hideOverflowingContent: false,
			content: hasCalledProcessInstances(diagram?.businessObjects) ? (
				<ProcessInstanceLink
					to="/operate/processes"
					search={{
						parentProcessInstanceKey: processInstanceKey,
						active: true,
						incidents: true,
						completed: true,
						canceled: true,
					}}
					title={t('operate.processInstance.header.viewAllTitle')}
					aria-label={t('operate.processInstance.header.viewAllTitle')}
					onClick={() =>
						storeStateLocally('operate.panelStates', {
							...getStateLocally('operate.panelStates'),
							isProcessesFiltersCollapsed: false,
						})
					}
				>
					{t('operate.processInstance.header.viewAll')}
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
			instanceName={name}
			state={state === 'SUSPENDED' ? state : hasIncident ? 'INCIDENT' : state}
			incidentsCount={hasIncident ? incidentsCount : 0}
			nameSubtitle={getWaitStateLabel(waitingCount)}
			headerColumns={columns.map(({name}) => name)}
			bodyColumns={columns}
			additionalContent={
				<>
					{draining?.byKey.has(processDefinitionKey) && (
						<DrainingTag
							label={t('operate.processInstance.header.draining')}
							description={t('operate.dashboard.drainingDescriptionVersion')}
							align="bottom-right"
						/>
					)}
					{operations}
				</>
			}
		/>
	);
}

export {ProcessInstanceHeader};
