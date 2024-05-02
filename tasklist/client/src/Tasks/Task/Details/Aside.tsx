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

  return (
    <aside className={styles.aside} aria-label="Task details right panel">
      <ContainedList label="Details" kind="disclosed">
        <>
          {taskTenant === undefined ? null : (
            <ContainedListItem>
              <span className={styles.itemHeading}>Tenant</span>
              <br />
              <span className={styles.itemBody}>{taskTenant.name}</span>
            </ContainedListItem>
          )}
        </>
        <ContainedListItem>
          <span className={styles.itemHeading}>Creation date</span>
          <br />
          <span className={styles.itemBody}>{formatDate(creationDate)}</span>
        </ContainedListItem>
        <ContainedListItem>
          <span className={styles.itemHeading}>Candidates</span>
          <br />
          {candidates.length === 0 ? (
            <span className={styles.itemBody}>No candidates</span>
          ) : null}
          {candidates.map((candidate) => (
            <Tag size="sm" type="gray" key={candidate}>
              {candidate}
            </Tag>
          ))}
        </ContainedListItem>
        {completionDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>Completion date</span>
            <br />
            <span className={styles.itemBody}>
              {formatDate(completionDate)}
            </span>
          </ContainedListItem>
        ) : null}
        <ContainedListItem>
          <span className={styles.itemHeading}>Due date</span>
          <br />
          <span className={styles.itemBody}>
            {dueDate ? formatDate(dueDate) : 'No due date'}
          </span>
        </ContainedListItem>
        {followUpDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>Follow up date</span>
            <br />
            <span className={styles.itemBody}>{formatDate(followUpDate)}</span>
          </ContainedListItem>
        ) : null}
      </ContainedList>
    </aside>
  );
};

export {Aside};
