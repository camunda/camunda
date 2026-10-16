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
import {Button, Link} from '@camunda/design-system';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {queries} from '#/shared/http/queries';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {InstanceHeader, type Column} from '#/operate/shared/InstanceHeader/shadcn.components/InstanceHeader';
import {InstanceHeaderSkeleton} from '#/operate/shared/InstanceHeader/shadcn.components/InstanceHeaderSkeleton';
import {formatEvaluationDate} from '#/operate/shared/utils/formatEvaluationDate';
import {useDecisionInstance} from '../decisionInstance.queries';
import {getHeaderColumns} from '../headerColumns';

type Props = {
	decisionEvaluationInstanceKey: string;
	onOpenDrd: () => void;
	openDrdButtonRef?: React.Ref<HTMLButtonElement>;
};

const InlineLink: React.FC<React.ComponentProps<'a'>> = (props) => <Link inline {...props} />;
const DecisionInstanceLink = createLink(InlineLink);

const Header: React.FC<Props> = ({decisionEvaluationInstanceKey, onOpenDrd, openDrdButtonRef}) => {
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
			processInstanceKey: decisionInstance.processInstanceKey,
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
				title: decisionInstance.processInstanceKey ?? t('operate.decisionInstance.header.noProcessInstance'),
				hideOverflowingContent: false,
				content: decisionInstance.processInstanceKey ? (
					<DecisionInstanceLink
						to="/operate/processes/$processInstanceId"
						params={{processInstanceId: decisionInstance.processInstanceKey}}
						title={processInstanceLinkTitle}
						aria-label={processInstanceLinkTitle}
					>
						{decisionInstance.processInstanceKey}
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
					<Button
						type="button"
						variant="secondary"
						size="sm"
						title={t('operate.decisionInstance.header.openDrdButton')}
						aria-label={t('operate.decisionInstance.header.openDrdButton')}
						ref={openDrdButtonRef}
						onClick={onOpenDrd}
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
