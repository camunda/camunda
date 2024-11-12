/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonText, Stack} from '@carbon/react';
import styles from './styles.module.scss';
import cn from 'classnames';

const TaskSkeleton: React.FC = () => {
  return (
    <article className={styles.taskSkeleton}>
      <Stack gap={3}>
        <div className={cn(styles.flex, styles.flexColumn)}>
          <span className={styles.name}>
            <SkeletonText width="250px" />
          </span>
          <span className={styles.label}>
            <SkeletonText width="200px" />
          </span>
        </div>
        <div className={cn(styles.flex, styles.flexColumn)}>
          <span className={styles.label}>
            <SkeletonText width="50px" />
          </span>
        </div>
        <div className={cn(styles.flex, styles.flexColumn)}>
          <span className={styles.label}>
            <SkeletonText width="100px" />
          </span>
        </div>
      </Stack>
    </article>
  );
};

export {TaskSkeleton};
