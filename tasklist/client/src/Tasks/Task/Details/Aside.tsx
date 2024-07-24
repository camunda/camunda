/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ContainedList, ContainedListItem, Tag} from '@carbon/react';
import {formatDate} from 'modules/utils/formatDate';
import styles from './Aside.module.scss';
import {CurrentUser, Task} from 'modules/types';
import {useTranslation} from 'react-i18next';

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
    candidateUsers,
    candidateGroups,
    tenantId,
  } = task;
  const taskTenant =
    user.tenants.length > 1
      ? user.tenants.find((tenant) => tenant.id === tenantId)
      : undefined;
  const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];
  const {t} = useTranslation();

  return (
    <aside className={styles.aside} aria-label={t('taskDetailsRightPanel')}>  
      <ContainedList label={t('detailsLabel')} kind="disclosed">
        <>
          {taskTenant === undefined ? null : (
            <ContainedListItem>
              <span className={styles.itemHeading}>{t('tenantLabel')}</span>
              <br />
              <span className={styles.itemBody}>{taskTenant.name}</span>
            </ContainedListItem>
          )}
        </>
        <ContainedListItem>
          <span className={styles.itemHeading}>{t('creationDateLabel')}</span>
          <br />
          <span className={styles.itemBody}>{formatDate(creationDate)}</span>
        </ContainedListItem>
        <ContainedListItem>
          <span className={styles.itemHeading}>{t('candidatesLabel')}</span>
          <br />
          {candidates.length === 0 ? (
            <span className={styles.itemBody}>{t('noCandidatesLabel')}</span>
          ) : null}
          {candidates.map((candidate) => (
            <Tag size="sm" type="gray" key={candidate}>
              {candidate}
            </Tag>
          ))}
        </ContainedListItem>
        {completionDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>{t('completionDateLabel')}</span>
            <br />
            <span className={styles.itemBody}>
              {formatDate(completionDate)}
            </span>
          </ContainedListItem>
        ) : null}
        <ContainedListItem>
          <span className={styles.itemHeading}>{t('dueDateLabel')}</span>
          <br />
          <span className={styles.itemBody}>
            {dueDate ? formatDate(dueDate) : t('noDueDateLabel')}
          </span>
        </ContainedListItem>
        {followUpDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>{t('followUpDateLabel')}</span>
            <br />
            <span className={styles.itemBody}>{formatDate(followUpDate)}</span>
          </ContainedListItem>
        ) : null}
      </ContainedList>
    </aside>
  );
};

export {Aside};
