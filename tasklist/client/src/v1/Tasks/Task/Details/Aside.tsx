/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ContainedList, ContainedListItem, Tag} from '@carbon/react';
import {formatDate} from 'common/dates/formatDate';
import styles from './Aside.module.scss';
import type {Task} from 'v1/api/types';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/identity';
import {useTranslation} from 'react-i18next';
import {getPriorityLabel} from 'common/tasks/getPriorityLabel';

type Props = {
  task: Task;
  user: CurrentUser;
};

const Aside: React.FC<Props> = ({task, user}) => {
  const {
    creationDate,
    completionDate,
    dueDate,
    followUpDate,
    priority,
    candidateUsers,
    candidateGroups,
    tenantId,
  } = task;
  const taskTenant =
    user.tenants.length > 1
      ? user.tenants.find((tenant) => tenant.tenantId === tenantId)
      : undefined;
  const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];
  const {t} = useTranslation();

  return (
    <aside className={styles.aside} aria-label={t('taskDetailsRightPanel')}>
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
        {priority === null ? null : (
          <ContainedListItem>
            <span className={styles.itemHeading}>
              {t('taskDetailsPriorityLabel')}
            </span>
            <br />
            <span className={styles.itemBody}>
              {getPriorityLabel(priority).short}
            </span>
          </ContainedListItem>
        )}
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
