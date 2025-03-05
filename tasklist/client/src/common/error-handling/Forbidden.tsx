/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import styles from './SomethingWentWrong/styles.module.scss';
import {Heading, Link, Stack} from '@carbon/react';
import {ErrorRobot} from 'common/images/error-robot';

const Forbidden: React.FC = () => {
  const {t} = useTranslation();

  return (
    <div className={styles.container}>
      <div className={styles.content}>
        <Stack gap={6} orientation="horizontal">
          <ErrorRobot />
          <Stack gap={4}>
            <Heading>
              {t('403 - You do not have access to this component')}
            </Heading>
            <p>
              {t(
                "It looks like you don't have the necessary permissions to access this\n" +
                  '        component. Please contact your cluster admin to get access.',
              )}
            </p>
            <Link href="https://docs.camunda.io/docs/self-managed/operate-deployment/operate-authentication/#resource-based-permissions">
              {t('Learn more about permissions')}
            </Link>
          </Stack>
        </Stack>
      </div>
    </div>
  );
};

export {Forbidden};
