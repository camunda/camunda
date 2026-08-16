/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ContainedList, ContainedListItem, Tag} from '#/shared/design-system-compat';
import {useTranslation} from 'react-i18next';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {getPriorityLabel} from '#/tasklist/modules/available-tasks/getPriorityLabel';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import styles from './Aside.module.scss';
import layoutStyles from './taskDetailsLayoutCommon.module.scss';

type Props = {
	creationDate: string;
	completionDate: string | null | undefined;
	dueDate: string | null | undefined;
	followUpDate: string | null | undefined;
	priority: number | null | undefined;
	candidateUsers: string[];
	candidateGroups: string[];
	tenantId: string;
	businessId: string | null | undefined;
	user: CurrentUser;
	// DS-only: true when rendered inside TaskDetailsLayout's Sheet (below
	// `xl`) instead of the grid column — the Sheet already has its own left
	// border, so .aside's would double up.
	hideBorder?: boolean;
};

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
	hideBorder = false,
}) => {
	const {t} = useTranslation();
	const taskTenant = user.tenants.length > 1 ? user.tenants.find((tenant) => tenant.tenantId === tenantId) : undefined;
	const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];

	return (
		<aside
			className={cn(
				layoutStyles.aside,
				featureFlags.dsTasklistUI && layoutStyles.asideDS,
				hideBorder && layoutStyles.asideNoBorderDS,
			)}
			aria-label={t('tasklist.taskDetailsRightPanel')}
		>
			{/* DS-only, inside the Sheet (hideBorder): no label here — the Sheet's
			    own SheetHeader/SheetTitle already carries the "Details" title,
			    avoiding a duplicated title and the ContainedList's own disclosed-
			    header row (and its background, which doesn't match the Sheet's
			    surface — see TaskDetailsLayout.tsx). */}
			<ContainedList label={hideBorder ? '' : t('tasklist.taskDetailsDetailsLabel')} kind="disclosed">
				<>
					{taskTenant === undefined ? null : (
						<ContainedListItem>
							<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsTenantLabel')}</span>
							<br />
							<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>{taskTenant.name}</span>
						</ContainedListItem>
					)}
				</>
				<ContainedListItem>
					<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsCreationDateLabel')}</span>
					<br />
					<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>{formatISODateTime(creationDate)?.absolute.text ?? creationDate}</span>
				</ContainedListItem>
				<ContainedListItem>
					<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsCandidatesLabel')}</span>
					<br />
					{candidates.length === 0 ? (
						<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>{t('tasklist.taskDetailsNoCandidatesLabel')}</span>
					) : null}
					{candidates.length === 0 ? null : featureFlags.dsTasklistUI ? (
						// DS-only wrapper — Legacy keeps candidates as direct siblings
						// (its Tag has its own default Carbon spacing convention;
						// wrapping it would touch old-UI markup for no reason).
						<div className="flex flex-wrap gap-1">
							{candidates.map((candidate) => (
								<Tag size="sm" type="gray" key={candidate}>
									{candidate}
								</Tag>
							))}
						</div>
					) : (
						candidates.map((candidate) => (
							<Tag size="sm" type="gray" key={candidate}>
								{candidate}
							</Tag>
						))
					)}
				</ContainedListItem>
				{typeof priority === 'number' ? (
					<ContainedListItem>
						<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsPriorityLabel')}</span>
						<br />
						<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>{getPriorityLabel(priority).short}</span>
					</ContainedListItem>
				) : null}
				{completionDate ? (
					<ContainedListItem>
						<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsCompletionDateLabel')}</span>
						<br />
						<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>
							{formatISODateTime(completionDate)?.absolute.text ?? completionDate}
						</span>
					</ContainedListItem>
				) : null}
				<ContainedListItem>
					<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsDueDateLabel')}</span>
					<br />
					<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>
						{dueDate ? (formatISODateTime(dueDate)?.absolute.text ?? dueDate) : t('tasklist.taskDetailsNoDueDateLabel')}
					</span>
				</ContainedListItem>
				{followUpDate ? (
					<ContainedListItem>
						<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsFollowUpDateLabel')}</span>
						<br />
						<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>{formatISODateTime(followUpDate)?.absolute.text ?? followUpDate}</span>
					</ContainedListItem>
				) : null}
				{businessId ? (
					<ContainedListItem>
						<span className={cn(styles.itemHeading, featureFlags.dsTasklistUI && styles.itemHeadingDS)}>{t('tasklist.taskDetailsBusinessIdLabel')}</span>
						<br />
						<span className={cn(styles.itemBody, featureFlags.dsTasklistUI && styles.itemBodyDS)}>{businessId}</span>
					</ContainedListItem>
				) : null}
			</ContainedList>
		</aside>
	);
};

export {Aside};
