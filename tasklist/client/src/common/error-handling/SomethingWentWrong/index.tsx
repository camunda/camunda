/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Heading, Stack} from '@carbon/react';
import {ErrorRobot} from 'common/images/error-robot';
import styles from './styles.module.scss';
import cn from 'classnames';
import {useTranslation} from 'react-i18next';

type Props = {
  className?: string;
  title?: string;
  message?: string;
  onRetryClick?: () => void;
};

const SomethingWentWrong: React.FC<Props> = ({
  className,
  title,
  message,
  onRetryClick = () => window.location.reload(),
}) => {
  const {t} = useTranslation();

  return (
    <div className={cn(className, styles.container)}>
      <div className={styles.content}>
        <Stack gap={6} orientation="horizontal">
          <ErrorRobot />
          <Stack gap={4}>
            <Heading>{title ?? t('errorGenericErrorTitle')}</Heading>
            <p>{message ?? t('errorGenericErrorMessage')}</p>
            <Button kind="primary" type="button" onClick={onRetryClick}>
              {t('errorGenericErrorButtonLabel')}
            </Button>
          </Stack>
        </Stack>
      </div>
    </div>
  );
};

export {SomethingWentWrong};
