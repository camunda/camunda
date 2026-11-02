/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge, linkVariants} from '@camunda/design-system';
import {CopiableContent} from '#/operate/shared/PanelHeader/shadcn.components/CopiableContent';
import {Link} from '@tanstack/react-router';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {InstanceHeader, type Column} from '#/operate/shared/InstanceHeader/shadcn.components/InstanceHeader';
import {DrainingTag} from '#/operate/components/DrainingTag/shadcn.components/DrainingTag';
import {drainingProcessDefinitionsQuery} from '#/operate/pages/Dashboard/InstancesByProcess/instancesByProcess.queries';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {hasCalledProcessInstances} from '#/operate/shared/utils/elements';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {getWaitStateLabel} from '#/operate/shared/utils/waitStates';
import {useMatchMedia} from '#/operate/shared/useMatchMedia';
import {processInstanceIncidentsCountQuery, useProcessInstanceWaitStateStatistics} from '../processInstance.queries';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {COLLAPSED_LAYOUT_QUERY} from './collapsedLayoutQuery';
import {ProcessInstanceHeaderSkeleton} from './ProcessInstanceHeaderSkeleton';

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
	const isReducedLayout = useMatchMedia(COLLAPSED_LAYOUT_QUERY);
	const {data: waitStateStatistics, isError: isWaitStateError} = useProcessInstanceWaitStateStatistics(instance);
	const waitingCount = isWaitStateError
		? 0
		: (waitStateStatistics?.find(({elementId}) => elementId === processDefinitionId)?.waitingCount ?? 0);
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const {data: currentUser} = useQuery(queries.getCurrentUser());
	const {data: draining} = useQuery(drainingProcessDefinitionsQuery());
	const {data: incidentsCount = 0} = useQuery(processInstanceIncidentsCountQuery(instance));
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
		{
			name: t('operate.processInstance.header.key'),
			hideOverflowingContent: false,
			content: (
				<CopiableContent
					content={processInstanceKey}
					copyButtonDescription={t('operate.processInstance.header.keyCopyButtonDescription')}
					className="-ml-3 [&>button]:h-5!"
				/>
			),
		},
		{
			name: t('operate.processInstance.header.version'),
			hideOverflowingContent: false,
			content: (
				<Link
					to="/operate-preview/processes"
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
					className={linkVariants()}
				>
					{processDefinitionVersion}
				</Link>
			),
		},
		{
			name: t('operate.processInstance.header.versionTag'),
			hidden: !hasVersionTag,
			title: t('operate.processInstance.header.versionTagDescription'),
			content: <Badge variant="neutral">{processDefinitionVersionTag}</Badge>,
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
		},
		{
			name: t('operate.processInstance.header.endDate'),
			hidden: endDate === null || isReducedLayout,
			title: formatTimestamp(endDate),
			content: formatTimestamp(endDate),
		},
		{
			name: t('operate.processInstance.header.calledInstances'),
			hidden: isReducedLayout,
			hideOverflowingContent: false,
			content: hasCalledProcessInstances(diagram?.businessObjects) ? (
				<Link
					to="/operate-preview/processes"
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
					className={linkVariants()}
					onClick={() =>
						storeStateLocally('operate.panelStates', {
							...(getStateLocally('operate.panelStates') ?? {}),
							isProcessesFiltersCollapsed: false,
						})
					}
				>
					{t('operate.processInstance.header.viewCalledInstances')}
				</Link>
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
			incidentsCount={incidentsCount}
			nameSubtitle={getWaitStateLabel(waitingCount)}
			headerColumns={columns.map(({name}) => name)}
			bodyColumns={columns}
			additionalContent={
				<div className="flex items-center gap-2">
					{isDraining && (
						<DrainingTag
							label={t('operate.dashboard.draining')}
							description={t('operate.dashboard.drainingDescriptionVersion')}
							align="bottom-right"
						/>
					)}
					{operations}
				</div>
			}
		/>
	);
};

export {ProcessInstanceHeader};
