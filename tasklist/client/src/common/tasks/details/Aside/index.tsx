/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ContainedList, ContainedListItem, Tag} from '@carbon/react';
import {formatDate} from 'common/dates/formatDate';
import styles from './styles.module.scss';
import taskDetailsLayoutCommon from 'common/tasks/details/taskDetailsLayoutCommon.module.scss';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.9';
import {useTranslation} from 'react-i18next';
import {getPriorityLabel} from 'common/tasks/getPriorityLabel';

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
  const taskTenant =
    user.tenants.length > 1
      ? user.tenants.find((tenant) => tenant.tenantId === tenantId)
      : undefined;
  const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];
  const {t} = useTranslation();

  return (
    <aside
      className={taskDetailsLayoutCommon.aside}
      aria-label={t('taskDetailsRightPanel')}
    >
      <ContainedList label={t('taskDetailsDetailsLabel')} kind="disclosed">
        <>
          {taskTenant === undefined ? null : (
            <ContainedListItem>
              <span className={styles.itemHeading}>
                {t('taskDetailsTenantLabel')}
              </span>
              <br />
              <span className={styles.itemBody}>{taskTenant.name}</span>
            </ContainedListItem>
          )}
        </>
        <ContainedListItem>
          <span className={styles.itemHeading}>
            {t('taskDetailsCreationDateLabel')}
          </span>
          <br />
          <span className={styles.itemBody}>{formatDate(creationDate)}</span>
        </ContainedListItem>
        <ContainedListItem>
          <span className={styles.itemHeading}>
            {t('taskDetailsCandidatesLabel')}
          </span>
          <br />
          {candidates.length === 0 ? (
            <span className={styles.itemBody}>
              {t('taskDetailsNoCandidatesLabel')}
            </span>
          ) : null}
          {candidates.map((candidate) => (
            <Tag size="sm" type="gray" key={candidate}>
              {candidate}
            </Tag>
          ))}
        </ContainedListItem>
        {typeof priority === 'number' ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>
              {t('taskDetailsPriorityLabel')}
            </span>
            <br />
            <span className={styles.itemBody}>
              {getPriorityLabel(priority).short}
            </span>
          </ContainedListItem>
        ) : null}
        {completionDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>
              {t('taskDetailsCompletionDateLabel')}
            </span>
            <br />
            <span className={styles.itemBody}>
              {formatDate(completionDate)}
            </span>
          </ContainedListItem>
        ) : null}
        <ContainedListItem>
          <span className={styles.itemHeading}>
            {t('taskDetailsDueDateLabel')}
          </span>
          <br />
          <span className={styles.itemBody}>
            {dueDate ? formatDate(dueDate) : t('taskDetailsNoDueDateLabel')}
          </span>
        </ContainedListItem>
        {followUpDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>
              {t('taskDetailsFollowUpDateLabel')}
            </span>
            <br />
            <span className={styles.itemBody}>{formatDate(followUpDate)}</span>
          </ContainedListItem>
        ) : null}
      </ContainedList>
    </aside>
  );
};

export {Aside};
