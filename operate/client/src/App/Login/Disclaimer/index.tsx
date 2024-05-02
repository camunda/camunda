/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from '@carbon/react';
import {Container, InlineLink} from './styled';
import {useState} from 'react';
import {TermsConditionsModal} from 'modules/components/TermsConditionsModal';

const Disclaimer: React.FC = () => {
  const [isTermsConditionModalOpen, setTermsConditionModalOpen] =
    useState<boolean>(false);

  return window.clientConfig?.isEnterprise ? null : (
    <>
      <Container>
        Non-Production License. If you would like information on production
        usage, please refer to our{' '}
        <InlineLink onClick={() => setTermsConditionModalOpen(true)} inline>
          terms & conditions page
        </InlineLink>{' '}
        or{' '}
        <Link href="https://camunda.com/contact/" target="_blank" inline>
          contact sales
        </Link>
        .
      </Container>
      <TermsConditionsModal
        isModalOpen={isTermsConditionModalOpen}
        onModalClose={() => setTermsConditionModalOpen(false)}
      />
    </>
  );
};

export {Disclaimer};
