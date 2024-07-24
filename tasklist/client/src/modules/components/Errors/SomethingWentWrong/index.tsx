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
import {useTranslation} from 'react-i18next';

const SomethingWentWrong: React.FC<{className?: string}> = ({className}) => {

  const {t} = useTranslation();

  return (
    <div className={cn(className, styles.container)}>
      <div className={styles.content}>
        <Stack gap={6} orientation="horizontal">
          <ErrorRobot />
          <Stack gap={4}>
            <Heading>{t('somethingWentWrongTitle')}</Heading>
            <p>{t('pageCouldNotBeLoaded')}</p>
            <Button kind="primary" onClick={() => window.location.reload()}>{t('tryAgain')}</Button>
          </Stack>
        </Stack>
      </div>
    </div>
  );
};

export {SomethingWentWrong};
