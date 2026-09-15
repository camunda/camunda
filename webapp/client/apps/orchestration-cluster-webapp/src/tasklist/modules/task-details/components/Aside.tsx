/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge, Text} from '@camunda/design-system';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {useTranslation} from 'react-i18next';
import {getPriorityLabel} from '#/tasklist/modules/available-tasks/getPriorityLabel';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

type Props = {
	creationDate: string;
	completionDate: string | null;
	dueDate: string | null;
	followUpDate: string | null;
	priority: number | null;
	candidateUsers: string[];
	candidateGroups: string[];
	tenantId: string;
	businessId: string | null;
	user: CurrentUser;
	showTitle?: boolean;
};

type DetailItemProps = {
	label: string;
	children: React.ReactNode;
};

const DetailItem: React.FC<DetailItemProps> = ({label, children}) => (
	<div className="px-4 py-3">
		<Text as="div" variant="body-sm" className="text-neutral-foreground-subtle">
			{label}
		</Text>
		<div className="text-sm leading-5 text-neutral-foreground-strong">{children}</div>
	</div>
);

const Aside: React.FC<Props> = ({
	creationDate,
	completionDate,
	dueDate,
	followUpDate,
	priority,
	candidateUsers,
	candidateGroups,
	tenantId,
	businessId,
	user,
	showTitle = true,
}) => {
	const {t} = useTranslation();
	const taskTenant = user.tenants.length > 1 ? user.tenants.find((tenant) => tenant.tenantId === tenantId) : undefined;
	const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];

	return (
		<aside className="h-full overflow-auto" aria-label={t('tasklist.taskDetailsRightPanel')}>
			{showTitle ? (
				<Text as="div" variant="label-md-strong" className="px-4 pb-2 text-neutral-foreground-strong">
					{t('tasklist.taskDetailsDetailsLabel')}
				</Text>
			) : null}
			{taskTenant === undefined ? null : (
				<DetailItem label={t('tasklist.taskDetailsTenantLabel')}>{taskTenant.name}</DetailItem>
			)}
			<DetailItem label={t('tasklist.taskDetailsCreationDateLabel')}>
				{formatISODateTime(creationDate)?.absolute.text ?? creationDate}
			</DetailItem>
			<DetailItem label={t('tasklist.taskDetailsCandidatesLabel')}>
				{candidates.length === 0 ? (
					t('tasklist.taskDetailsNoCandidatesLabel')
				) : (
					<div className="flex flex-wrap gap-1 pt-1">
						{candidates.map((candidate) => (
							<Badge variant="neutral" key={candidate}>
								{candidate}
							</Badge>
						))}
					</div>
				)}
			</DetailItem>
			{typeof priority === 'number' ? (
				<DetailItem label={t('tasklist.taskDetailsPriorityLabel')}>{getPriorityLabel(priority).short}</DetailItem>
			) : null}
			{completionDate ? (
				<DetailItem label={t('tasklist.taskDetailsCompletionDateLabel')}>
					{formatISODateTime(completionDate)?.absolute.text ?? completionDate}
				</DetailItem>
			) : null}
			<DetailItem label={t('tasklist.taskDetailsDueDateLabel')}>
				{dueDate ? (formatISODateTime(dueDate)?.absolute.text ?? dueDate) : t('tasklist.taskDetailsNoDueDateLabel')}
			</DetailItem>
			{followUpDate ? (
				<DetailItem label={t('tasklist.taskDetailsFollowUpDateLabel')}>
					{formatISODateTime(followUpDate)?.absolute.text ?? followUpDate}
				</DetailItem>
			) : null}
			{businessId ? <DetailItem label={t('tasklist.taskDetailsBusinessIdLabel')}>{businessId}</DetailItem> : null}
		</aside>
	);
};

export {Aside};
