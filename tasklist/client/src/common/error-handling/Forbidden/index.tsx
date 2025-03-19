/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation, Trans} from 'react-i18next';
import {Link, Stack} from '@carbon/react';
import {Launch} from '@carbon/react/icons';
import Icon from './forbidden.svg?react';
import styles from './styles.module.scss';

const Forbidden: React.FC = () => {
  const {t} = useTranslation();

  return (
    <div className={styles.container}>
      <div className={styles.content}>
        <Stack gap={6}>
          <Icon />
          <Stack gap={3}>
            <h3 className={styles.title}>{t('forbiddenPageTitle')}</h3>
            <div className={styles.description}>
              <Trans
                i18nKey="forbiddenPageDesc"
                components={{
                  strong: <strong />,
                }}
              />
            </div>
          </Stack>
          <Link
            href="https://docs.camunda.io/docs/next/components/concepts/access-control/authorizations/"
            target="_blank"
            renderIcon={Launch}
          >
            {t('forbiddenPageLinkLabel')}
          </Link>
        </Stack>
      </div>
    </div>
  );
};

export {Forbidden};
