/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from '@carbon/react';
import styles from './styles.module.scss';
import {Trans} from 'react-i18next';

type Props = {
  className?: string;
};

const Disclaimer: React.FC<Props> = () => {
  return window.clientConfig?.isEnterprise ? null : (
    <span className={styles.container}>
      <Trans i18nKey="nonProductionLicenseLinks">
        Non-Production License. If you would like information on production usage,
        please refer to our
        <Link
          href="https://legal.camunda.com/#self-managed-non-production-terms"
          target="_blank"
          inline
        >
          terms & conditions page
        </Link>
        or
        <Link href="https://camunda.com/contact/" target="_blank" inline>
          contact sales
        </Link>
        .
      </Trans>
    </span>
  );
};

export {Disclaimer};
