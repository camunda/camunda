/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TaskSkeleton} from './Task/TaskSkeleton';
import styles from './styles.module.scss';

const Skeleton: React.FC = () => {
  return (
    <div data-testid="tasks-skeleton" className={styles.listContainer}>
      {Array.from({length: 50}).map((_, index) => (
        <TaskSkeleton key={index} />
      ))}
    </div>
  );
};

export {Skeleton};
