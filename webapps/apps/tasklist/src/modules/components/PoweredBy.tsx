/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link, SkeletonPlaceholder} from '@carbon/react';
import {CamundaLogo} from 'modules/components/CamundaLogo';
import styles from './PoweredBy.module.scss';
import cn from 'classnames';

type PoweredByProps = {
  className?: string;
};

const PoweredBy: React.FC<PoweredByProps> = ({className}) => {
  return (
    <p className={cn(className, styles.body)}>
      Powered by{' '}
      <Link href="https://camunda.com/" target="_blank">
        <CamundaLogo className={styles.logo} aria-label="Camunda" />
      </Link>
    </p>
  );
};

const SkeletonPoweredBy: React.FC = () => {
  return (
    <span className={styles.body}>
      <SkeletonPlaceholder className={styles.skeletonFooter} />
      <SkeletonPlaceholder className={styles.skeletonLogo} />
    </span>
  );
};

export {PoweredBy, SkeletonPoweredBy};
