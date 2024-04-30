/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from '@carbon/react';
import styles from './styles.module.scss';
import {useState} from 'react';
import {TermsConditionsModal} from 'modules/components/TermsConditionsModal';

type Props = {
  className?: string;
};

const Disclaimer: React.FC<Props> = () => {
  const [isTermsConditionModalOpen, setTermsConditionModalOpen] =
    useState(false);

  return window.clientConfig?.isEnterprise ? null : (
    <>
      <span className={styles.container}>
        Non-Production License. If you would like information on production
        usage, please refer to our{' '}
        <Link
          href="#"
          onClick={(event) => {
            event.preventDefault();
            setTermsConditionModalOpen(true);
          }}
          inline
        >
          terms & conditions page
        </Link>{' '}
        or{' '}
        <Link href="https://camunda.com/contact/" target="_blank" inline>
          contact sales
        </Link>
        .
      </span>
      <TermsConditionsModal
        isModalOpen={isTermsConditionModalOpen}
        onModalClose={() => setTermsConditionModalOpen(false)}
      />
    </>
  );
};

export {Disclaimer};
