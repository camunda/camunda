/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Link} from '@carbon/react';
import {Link as RouterLink} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import type {TFunction} from 'i18next';
import {tracking} from '#/shared/tracking';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {queries} from '#/shared/http/queries';
import {InstanceHeader, type Column} from '#/operate/shared/InstanceHeader/InstanceHeader';
import {InstanceHeaderSkeleton} from '#/operate/shared/InstanceHeader/InstanceHeaderSkeleton';
import {useDecisionInstance} from './decisionInstance.queries';
import {formatEvaluationDate} from './utils';

type HeaderColumn = {name: string; skeletonWidth: string};

function getHeaderColumns(
	t: TFunction,
	options: {isMultiTenancyEnabled?: boolean; hasBusinessId?: boolean},
): HeaderColumn[] {
	const {isMultiTenancyEnabled = false, hasBusinessId = false} = options;
	return [
		{name: t('operate.decisionInstance.header.decisionInstanceKeyColumn'), skeletonWidth: '137px'},
		{name: t('operate.decisionInstance.header.versionColumn'), skeletonWidth: '33px'},
		...(hasBusinessId ? [{name: t('operate.decisionInstance.header.businessIdColumn'), skeletonWidth: '137px'}] : []),
		...(isMultiTenancyEnabled
			? [{name: t('operate.decisionInstance.header.tenantColumn'), skeletonWidth: '34px'}]
			: []),
		{name: t('operate.decisionInstance.header.evaluationDateColumn'), skeletonWidth: '143px'},
		{name: t('operate.decisionInstance.header.processInstanceColumn'), skeletonWidth: '137px'},
	];
}

type Props = {
	decisionEvaluationInstanceKey: string;
	onOpenDrd: () => void;
};

const Header: React.FC<Props> = ({decisionEvaluationInstanceKey, onOpenDrd}) => {
	const {t} = useTranslation();
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const {data: tenants} = useSuspenseQuery({...queries.getCurrentUser(), select: ({tenants}) => tenants});
	const tenantsById = Object.fromEntries(tenants.map(({tenantId, name}) => [tenantId, name]));
	const {data: decisionInstance, status} = useDecisionInstance(decisionEvaluationInstanceKey);

	if (status === 'pending') {
		return <InstanceHeaderSkeleton headerColumns={getHeaderColumns(t, {isMultiTenancyEnabled})} />;
	}

	if (status === 'success' && decisionInstance !== null) {
		const tenantId = decisionInstance.tenantId;
		const tenantName = tenantsById[tenantId] ?? tenantId;
		const hasBusinessId = decisionInstance.businessId !== null;
		const headerColumns = getHeaderColumns(t, {isMultiTenancyEnabled, hasBusinessId});
		const versionLinkTitle = isMultiTenancyEnabled
			? t('operate.decisionInstance.header.versionLinkTitleWithTenant', {
					name: decisionInstance.decisionDefinitionName,
					version: decisionInstance.decisionDefinitionVersion,
					tenant: tenantName,
				})
			: t('operate.decisionInstance.header.versionLinkTitle', {
					name: decisionInstance.decisionDefinitionName,
					version: decisionInstance.decisionDefinitionVersion,
				});

		const bodyColumns: Column[] = [
			{
				title: decisionInstance.decisionEvaluationInstanceKey,
				content: decisionInstance.decisionEvaluationInstanceKey,
			},
			{
				hideOverflowingContent: false,
				content: (
					// TODO(#55977): point at the filtered Decisions list once its search schema exists
					<Link
						as={RouterLink}
						to="/operate/decisions"
						title={versionLinkTitle}
						aria-label={versionLinkTitle}
						onClick={() => {
							tracking.track({eventName: 'operate:navigation', link: 'decision-details-version'});
						}}
					>
						{decisionInstance.decisionDefinitionVersion}
					</Link>
				),
			},
			{
				hidden: !hasBusinessId,
				title: decisionInstance.businessId ?? undefined,
				content: decisionInstance.businessId,
			},
			{
				hidden: !isMultiTenancyEnabled,
				title: tenantName,
				content: tenantName,
			},
			{
				title: formatEvaluationDate(decisionInstance.evaluationDate),
				content: formatEvaluationDate(decisionInstance.evaluationDate),
			},
			{
				title: decisionInstance.processInstanceKey ?? t('operate.decisionInstance.header.noProcessInstance'),
				hideOverflowingContent: false,
				content: decisionInstance.processInstanceKey ? (
					// TODO(#56029): point at the real Process Instance route once it exists
					<Link
						as={RouterLink}
						to="/"
						title={t('operate.decisionInstance.header.processInstanceLinkTitle', {
							processInstanceKey: decisionInstance.processInstanceKey,
						})}
						aria-label={t('operate.decisionInstance.header.processInstanceLinkTitle', {
							processInstanceKey: decisionInstance.processInstanceKey,
						})}
						onClick={() => {
							tracking.track({eventName: 'operate:navigation', link: 'decision-details-parent-process-details'});
						}}
					>
						{decisionInstance.processInstanceKey}
					</Link>
				) : (
					t('operate.decisionInstance.header.noProcessInstance')
				),
			},
		];

		return (
			<InstanceHeader
				state={decisionInstance.state}
				instanceName={decisionInstance.decisionDefinitionName}
				incidentsCount={decisionInstance.state === 'FAILED' ? 1 : 0}
				headerColumns={headerColumns.map(({name}) => name)}
				bodyColumns={bodyColumns}
				additionalContent={
					<Button
						size="sm"
						kind="tertiary"
						title={t('operate.decisionInstance.header.openDrdButton')}
						aria-label={t('operate.decisionInstance.header.openDrdButton')}
						onClick={() => {
							onOpenDrd();
							tracking.track({eventName: 'operate:drd-panel-interaction', action: 'open'});
						}}
					>
						{t('operate.decisionInstance.header.openDrdButton')}
					</Button>
				}
			/>
		);
	}

	return null;
};

export {Header};
