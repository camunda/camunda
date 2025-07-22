/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Loading} from '@carbon/react';
import type {UserTaskState} from '@vzeta/camunda-api-zod-schemas/8.8';
import styles from './TaskStateLoadingText.module.scss';
import {t} from 'i18next';

type TaskStateLoadingTextProps = {taskState: UserTaskState};

const statusLoadingMessage = {
  CREATED: null,
  ASSIGNING: null,
  COMPLETED: null,
  CANCELED: null,
  FAILED: null,
  UPDATING: t('Please wait while the task is being updated...'),
  CANCELING: t('The task is being cancelled. Please wait...'),
  COMPLETING: t('Completing...'),
};

const TaskStateLoadingText: React.FC<TaskStateLoadingTextProps> = ({
  taskState,
}) => {
  if (statusLoadingMessage[taskState] === null) return null;

  return (
    <div className={styles.container}>
      <Loading small withOverlay={false} />
      <p className={styles.message}>{statusLoadingMessage[taskState]}</p>
    </div>
  );
};

export {TaskStateLoadingText};
