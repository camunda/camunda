/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ContainedList,
  ContainedListItem,
  SkeletonText,
  ButtonSkeleton,
} from '@carbon/react';
import styles from './styles.module.scss';

const Skeleton: React.FC = () => {
  return (
    <ContainedList
      label={<SkeletonText width="20%" />}
      kind="on-page"
      isInset
      className={styles.containedList}
      data-testid="attachment-list-skeleton"
    >
      <ContainedListItem action={<ButtonSkeleton size="sm" />}>
        <SkeletonText width="40%" />
      </ContainedListItem>
      <ContainedListItem action={<ButtonSkeleton size="md" />}>
        <SkeletonText width="55%" />
      </ContainedListItem>
      <ContainedListItem action={<ButtonSkeleton size="md" />}>
        <SkeletonText width="35%" />
      </ContainedListItem>
    </ContainedList>
  );
};

export {Skeleton};
