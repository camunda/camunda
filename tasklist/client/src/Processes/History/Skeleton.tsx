/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonText, Stack} from '@carbon/react';
import styles from './styles.module.scss';

const Skeleton: React.FC = () => {
  return (
    <div data-testid="history-skeleton">
      {Array.from({length: 50}).map((_, index) => (
        <Stack key={index} gap={3} className={styles.item}>
          <SkeletonText className={styles.skeletonText} width="225px" />
          <SkeletonText className={styles.skeletonText} width="175px" />
          <SkeletonText className={styles.skeletonText} width="200px" />
        </Stack>
      ))}
    </div>
  );
};

export {Skeleton};
