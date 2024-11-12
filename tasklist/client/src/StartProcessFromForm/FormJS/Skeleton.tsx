/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ButtonSkeleton, SkeletonText, TextAreaSkeleton} from '@carbon/react';
import {SkeletonPoweredBy} from 'modules/components/PoweredBy';
import styles from './styles.module.scss';

const Skeleton: React.FC = () => {
  return (
    <div
      className={styles.container}
      data-testid="public-form-skeleton"
      aria-busy="true"
    >
      <div className={styles.formContainer}>
        <div className={styles.formSkeletonContainer}>
          <SkeletonText heading />
          <TextAreaSkeleton />
          <TextAreaSkeleton />
          <TextAreaSkeleton />
          <TextAreaSkeleton />
        </div>
      </div>
      <div className={styles.submitButtonRow}>
        <ButtonSkeleton size="lg" />
        <SkeletonPoweredBy />
      </div>
    </div>
  );
};

export {Skeleton};
