/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createLink} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Button, Link, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Workflow} from '@camunda/design-system/icons';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {queries} from '#/shared/http/queries';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {InstanceHeader, type Column} from '#/operate/shared/InstanceHeader/shadcn.components/InstanceHeader';
import {InstanceHeaderSkeleton} from '#/operate/shared/InstanceHeader/shadcn.components/InstanceHeaderSkeleton';
import {formatEvaluationDate} from '#/operate/shared/utils/formatEvaluationDate';
import {useDecisionInstance} from '../decisionInstance.queries';
import {getHeaderColumns} from './headerColumns';

type Props = {
	decisionEvaluationInstanceKey: string;
	onOpenDrd: () => void;
	isDrdOpen?: boolean;
	openDrdButtonRef?: React.Ref<HTMLButtonElement>;
};

const DecisionInstanceLink = createLink(Link);

const Header: React.FC<Props> = ({decisionEvaluationInstanceKey, onOpenDrd, isDrdOpen = false, openDrdButtonRef}) => {
	const {t} = useTranslation();
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const {data: tenants} = useSuspenseQuery({...queries.getCurrentUser(), select: ({tenants}) => tenants});
	const tenantsById = Object.fromEntries(tenants.map(({tenantId, name}) => [tenantId, name]));
	const {query, isGenericError} = useDecisionInstance(decisionEvaluationInstanceKey);
	const {data: decisionInstance, status, refetch} = query;

	if (isGenericError) {
		return <GenericErrorPage reset={() => void refetch()} />;
	}

	if (status === 'pending') {
		return <InstanceHeaderSkeleton headerColumns={getHeaderColumns(t, {isMultiTenancyEnabled})} />;
	}

	if (status === 'success' && decisionInstance !== null) {
		const tenantId = decisionInstance.tenantId;
		const tenantName = tenantsById[tenantId] ?? tenantId;
		const processInstanceKey =
			decisionInstance.processInstanceKey === null || decisionInstance.processInstanceKey === '-1'
				? null
				: decisionInstance.processInstanceKey;
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
		const processInstanceLinkTitle = t('operate.decisionInstance.header.processInstanceLinkTitle', {
			processInstanceKey,
		});

		const bodyColumns: Column[] = [
			{
				title: decisionInstance.decisionEvaluationInstanceKey,
				content: decisionInstance.decisionEvaluationInstanceKey,
			},
			{
				hideOverflowingContent: false,
				content: (
					<DecisionInstanceLink
						to="/operate-preview/decisions"
						search={{
							decisionDefinitionId: decisionInstance.decisionDefinitionId,
							decisionDefinitionVersion: decisionInstance.decisionDefinitionVersion,
							evaluated: true,
							failed: true,
							...(isMultiTenancyEnabled ? {tenantId} : {}),
						}}
						title={versionLinkTitle}
						aria-label={versionLinkTitle}
					>
						{decisionInstance.decisionDefinitionVersion}
					</DecisionInstanceLink>
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
				title: processInstanceKey ?? t('operate.decisionInstance.header.noProcessInstance'),
				hideOverflowingContent: false,
				content:
					processInstanceKey !== null ? (
						<DecisionInstanceLink
							to="/operate/processes/$processInstanceId"
							params={{processInstanceId: processInstanceKey}}
							title={processInstanceLinkTitle}
							aria-label={processInstanceLinkTitle}
						>
							{processInstanceKey}
						</DecisionInstanceLink>
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
					!isDrdOpen && (
						<Tooltip>
							<TooltipTrigger asChild>
								<Button
									type="button"
									variant="secondary"
									size="sm"
									aria-label={t('operate.decisionInstance.ds.header.openDrdButton')}
									ref={openDrdButtonRef}
									onClick={onOpenDrd}
								>
									<Workflow aria-hidden />
									<span className="-ml-1.5 grid grid-cols-[0fr] transition-[grid-template-columns,margin] duration-150 ease-out lg:ml-0 lg:grid-cols-[1fr]">
										<span className="overflow-hidden whitespace-nowrap">
											{t('operate.decisionInstance.ds.header.openDrdButton')}
										</span>
									</span>
								</Button>
							</TooltipTrigger>
							<TooltipContent side="bottom">{t('operate.decisionInstance.ds.header.openDrdButton')}</TooltipContent>
						</Tooltip>
					)
				}
			/>
		);
	}

	return null;
};

export {Header};
