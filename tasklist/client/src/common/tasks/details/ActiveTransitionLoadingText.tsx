/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Loading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import styles from './ActiveTransitionLoadingText.module.scss';

type ActiveTransitionLoadingTextProps = {
  taskState:
    | 'CREATED'
    | 'COMPLETED'
    | 'CANCELED'
    | 'FAILED'
    | 'ASSIGNING'
    | 'UPDATING'
    | 'COMPLETING'
    | 'CANCELING';
};

const ActiveTransitionLoadingText: React.FC<
  ActiveTransitionLoadingTextProps
> = ({taskState}) => {
  const {t} = useTranslation();

  const statusLoadingMessage = {
    CREATED: null,
    ASSIGNING: null,
    COMPLETED: null,
    CANCELED: null,
    FAILED: null,
    UPDATING: t('taskStateUpdatingMessage'),
    CANCELING: t('taskStateCancelingMessage'),
    COMPLETING: t('taskStateCompletingMessage'),
  };

  if (statusLoadingMessage[taskState] === null) {
    return null;
  }

  return (
    <div className={styles.container}>
      <Loading small withOverlay={false} />
      <p className={styles.message}>{statusLoadingMessage[taskState]}</p>
    </div>
  );
};

export {ActiveTransitionLoadingText};
