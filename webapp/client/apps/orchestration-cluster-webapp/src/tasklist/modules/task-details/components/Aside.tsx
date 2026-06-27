/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ContainedList, ContainedListItem, Tag} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {getPriorityLabel} from '#/tasklist/modules/available-tasks/getPriorityLabel';
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
	user: CurrentUser;
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
	user,
}) => {
	const {t} = useTranslation();
	const taskTenant = user.tenants.length > 1 ? user.tenants.find((tenant) => tenant.tenantId === tenantId) : undefined;
	const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];

	return (
		<aside className={layoutStyles.aside} aria-label={t('tasklist.taskDetailsRightPanel')}>
			<ContainedList label={t('tasklist.taskDetailsDetailsLabel')} kind="disclosed">
				<>
					{taskTenant === undefined ? null : (
						<ContainedListItem>
							<span className={styles.itemHeading}>{t('tasklist.taskDetailsTenantLabel')}</span>
							<br />
							<span className={styles.itemBody}>{taskTenant.name}</span>
						</ContainedListItem>
					)}
				</>
				<ContainedListItem>
					<span className={styles.itemHeading}>{t('tasklist.taskDetailsCreationDateLabel')}</span>
					<br />
					<span className={styles.itemBody}>{formatISODateTime(creationDate)?.absolute.text ?? creationDate}</span>
				</ContainedListItem>
				<ContainedListItem>
					<span className={styles.itemHeading}>{t('tasklist.taskDetailsCandidatesLabel')}</span>
					<br />
					{candidates.length === 0 ? (
						<span className={styles.itemBody}>{t('tasklist.taskDetailsNoCandidatesLabel')}</span>
					) : null}
					{candidates.map((candidate) => (
						<Tag size="sm" type="gray" key={candidate}>
							{candidate}
						</Tag>
					))}
				</ContainedListItem>
				{typeof priority === 'number' ? (
					<ContainedListItem>
						<span className={styles.itemHeading}>{t('tasklist.taskDetailsPriorityLabel')}</span>
						<br />
						<span className={styles.itemBody}>{getPriorityLabel(priority).short}</span>
					</ContainedListItem>
				) : null}
				{completionDate ? (
					<ContainedListItem>
						<span className={styles.itemHeading}>{t('tasklist.taskDetailsCompletionDateLabel')}</span>
						<br />
						<span className={styles.itemBody}>
							{formatISODateTime(completionDate)?.absolute.text ?? completionDate}
						</span>
					</ContainedListItem>
				) : null}
				<ContainedListItem>
					<span className={styles.itemHeading}>{t('tasklist.taskDetailsDueDateLabel')}</span>
					<br />
					<span className={styles.itemBody}>
						{dueDate ? (formatISODateTime(dueDate)?.absolute.text ?? dueDate) : t('tasklist.taskDetailsNoDueDateLabel')}
					</span>
				</ContainedListItem>
				{followUpDate ? (
					<ContainedListItem>
						<span className={styles.itemHeading}>{t('tasklist.taskDetailsFollowUpDateLabel')}</span>
						<br />
						<span className={styles.itemBody}>{formatISODateTime(followUpDate)?.absolute.text ?? followUpDate}</span>
					</ContainedListItem>
				) : null}
			</ContainedList>
		</aside>
	);
};

export {Aside};
