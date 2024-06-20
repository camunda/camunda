/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Heading, Stack} from '@carbon/react';
import {ErrorRobot} from 'modules/images/error-robot';
import styles from './styles.module.scss';
import cn from 'classnames';

const SomethingWentWrong: React.FC<{className?: string}> = ({className}) => {
  return (
    <div className={cn(className, styles.container)}>
      <div className={styles.content}>
        <Stack gap={6} orientation="horizontal">
          <ErrorRobot />
          <Stack gap={4}>
            <Heading>Something went wrong</Heading>
            <p>This page could not be loaded. Try again later.</p>
            <Button kind="primary" onClick={() => window.location.reload()}>
              Try again
            </Button>
          </Stack>
        </Stack>
      </div>
    </div>
  );
};

export {SomethingWentWrong};
